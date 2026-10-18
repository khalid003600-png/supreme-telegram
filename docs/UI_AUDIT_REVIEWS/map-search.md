# تدقيق واجهة الخريطة والبحث — WolFox GPS 3.1.1/313

**النطاق المنفّذ:** `MapDialog.java` و`SearchDialog.java` و`MapSearchResolver.java` و`MapLinkParser.java`، مع اختبار Robolectric جديد فقط. لم يُعدَّل HTML أو منطق GPS أو Gradle أو manifest أو موارد مشتركة أو ملفات التفعيل/الحماية أو APK طرف ثالث.

> **حالة التحقق:** هذا فحص ساكن ومراجعة كود واختبارات أضيفت فقط. التزاماً بالقيد، لم يُشغَّل Gradle أو lint أو JUnit/Robolectric أو APK، ولم يُستخدم هاتف/host runtime أو شبكة تفعيل أو بيانات اعتماد. تنفيذ الاختبار الكامل مسؤولية التجميع الأب.

## الملخص التنفيذي

تم إصلاح أخطاء واجهة مؤكدة تتعلق بعمر `WebView` و`Dialog`، ونتائج البحث المتأخرة، والإلغاء الفعلي لعمل geocoder/HTTP، وتخطيط البحث عند الارتفاع القصير و`fontScale=2`، وتوافق API 21، وتحميل أصل الخريطة عند تضمين الموديول في LSPatch.

* لم تعد `MapDialog` تحتفظ بـ `WebView` ثابت إطلاقاً، ولم تعد تحتفظ بـ `Dialog` بقوة؛ المرجع الثابت الباقي ضعيف ومُصفّر عند الإغلاق.
* إغلاق/تعديل حوار البحث يلغي الطلب نفسه، لا يكتفي بتجاهل النتيجة في الواجهة.
* bridge الخاص بـ WebView يلغي الطلب الأقدم، ويرفض نتيجة أي generation قديم أو bridge دُمّر.
* مورد `wolfox_map.html` يُطلب أولاً من `MapDialog.class.getClassLoader()` (`assets/wolfox_map.html`) ثم يعود إلى أصول المضيف فقط عند عدم وجوده؛ وهذا يوافق نمط LSPatch ذي cache-APK/classloader المنفصل.

## الأدلة والإصلاحات

| المجال | قبل الإصلاح / الدليل | الإصلاح الحالي / الدليل | الأثر قبل → بعد |
|---|---|---|---|
| تسرب الحوار وWebView | كان `MapDialog.java:55-56` يحتفظ بـ `static Dialog currentDialog` و`static WebView currentWebView` بقوة. وكان `onDismiss` يدمر WebView فقط عندما يساوي المرجع الثابت (`MapDialog.java:292-297` قبل الإصلاح)، ما يترك WebView حوار قديم بلا تنظيف إذا استُبدل بسرعة. | `MapDialog.java:56` يحتفظ فقط بـ `WeakReference<Dialog>`؛ لا يوجد حقل `static WebView`. كل `onDismiss` يدمر WebView الخاص به بلا شرط في `MapDialog.java:306-312`. | مرجع نشاط/واجهة قوي ثابت واحتمال بقاء WebView قديم → ملكية واضحة لكل حوار وتنظيف حتمي. |
| خيط واجهة WebView | `Preview.destroy()` كان يدمر WebView مباشرة بغض النظر عن الخيط. | `MapDialog.java:74-82` يرحّل التنظيف إلى `MainLooper` إن لم يكن الاستدعاء على UI thread. `dismiss()` نفسه يُرحّل إلى MainLooper في `MapDialog.java:132-144`. | استدعاء WebView/Dialog من خيط خلفي محتمل → عمليات واجهة دائماً على main thread. |
| تنظيف WebView والجسر والأب | التنظيف السابق كان `stopLoading/loadUrl/removeAllViews/destroy` فقط (`MapDialog.java:477-485` قبل الإصلاح). | `MapDialog.java:511-530`: يلغي bridge، يوقف التحميل، يستدعي `onPause()`، يزيل `WolFoxSearchBridge`، يفصل `WebChromeClient` و`WebViewClient`، يزيل WebView من `ViewGroup` الأب، يمسح history ثم يدمره. | callbacks/JS bridge/parent يمكن أن يبقوا بعد dismiss → لا bridge أو parent أو عميل بعد التدمير. لم يُستخدم `WebView.pauseTimers()` عمداً لأنه global ويمكنه إيقاف WebViews المضيف غير المملوكة؛ `onPause()+about:blank+destroy` تنظيف خاص بالنسخة. |
| تحميل متأخر بعد destroy | `webView.post(loadMap)` أو callback قد يصل بعد الإغلاق. | `MapDialog.java:444-476` يتحقق من `isWebViewLive` وملكية Activity قبل التحميل ومجدداً قبل `loadDataWithBaseURL`. `MapDialog.java:611-613` يتطلب parent حيّاً. | تحميل/تقييم JavaScript على WebView منزوع → تجاهل آمن بلا لمس View مدمر. |
| نتائج WebView stale/double | bridge كان يبدأ `MapSearchResolver.resolve` لكل طلب بلا مقبض إلغاء أو generation (`MapDialog.java:509-528` قبل الإصلاح). | `MapDialog.java:536-598` يحتفظ بـ `activeRequest` و`requestGeneration` داخل bridge متزامن؛ يلغي السابق، ويرفض generation أقدم، و`destroy()` يلغي النشط. | بحثان متتابعان أو إغلاق قد يعرضان نتيجة قديمة → آخر طلب حي فقط يحق له تقييم JS. |
| إلغاء resolver/geocoder/HTTP | resolver كان يعيد `void` ويضمن callback بعد المهلة فقط (`MapSearchResolver.java:65-113` قبل الإصلاح)؛ dismiss لا يوقف geocoder أو HTTP. | `MapSearchResolver.java:46-122` يعيد `Request`. `RequestHandle` في `:352-405` يلغي future وtimeout ويفصل `HttpURLConnection`. Nominatim/Photon يسجلان الاتصال في `:241-295`، وتوسعة الروابط القصيرة تسجل الاتصال أيضاً عبر `MapLinkParser.java:98-137`. geocoder يلغي `Future` عند interruption/timeout في `MapSearchResolver.java:184-197`. | تكلفة خلفية وcallback بعد رحيل المالك → إلغاء فعلي واتصال مفصول ولا callback لـ Request ملغى. |
| dismiss/تعديل البحث | `SearchDialog` كان يزيد `requestId` ويخفي progress، لكن لا يحتفظ بالطلب ولا يلغيه (`SearchDialog.java:185-277` قبل الإصلاح). | `SearchDialog.java:174-176` يحتفظ بـ `activeSearch`; الإلغاء عند مسح/تحرير/بحث جديد في `:193-217,241`، وعند `onDismiss` في `:351-356`. callback يطابق identity للطلب وdialog/activity في `:257-291`. | نتيجة geocoder/HTTP من نص سابق أو بعد dismiss → لا تغيّر أزرار أو اختياراً أو نصاً قديماً. |
| تأكيد المحاكاة المؤجل | تأكيد البحث والخريطة كانا ينشئان `Handler.postDelayed(220ms)` بلا إزالته عند dismiss. | خريطة: `MapDialog.java:326-365` و`305-312`. بحث: `SearchDialog.java:306-356`. كل Runnable يفحص dialog/activity/request ويُزال من handler عند dismiss. | Toast/زر/Panel يمكن أن يعود بعد إغلاق الحوار → لا تنفيذ متأخر على واجهة منتهية. |
| تخطيط قصير / خط كبير | search يستخدم ScrollView غير ممتلئ ولا يفرض سقف ارتفاع نافذة مناسباً (`SearchDialog.java:55-58,330-334` قبل الإصلاح). | `SearchDialog.java:56` يستخدم `setFillViewport(true)`، والنافذة 90% من الارتفاع في `:358-362`؛ تبقى الإجراءات عمودية وقابلة للتمرير. | footer أو الأزرار السفلية قد تُقص في landscape/خط كبير → scroll أصلي للوصول ولا تراكب. |
| API 21 وRTL | `View.TEXT_DIRECTION_FIRST_STRONG_LTR` كان مباشرة في `SearchDialog.java:88` رغم minSdk 21؛ زر التوسيع استخدم `Gravity.LEFT` في `MapDialog.java:107` قبل الإصلاح. | guard API 23/بديل LTR في `SearchDialog.java:89-93`، و`Gravity.START` في `MapDialog.java:104`. كما أزيلت حارستا SDK غير اللازمتين لـ API 21 من outline/mixed content (`MapDialog.java:89-90,418`). | `InlinedApi` و`RtlHardcoded` و`ObsoleteSdkInt` في النطاق → متوافق minSdk 21 واتجاه صحيح. |
| معاينة المفضلة readonly | spinner تغيير نوع الخريطة كان معروضاً في معاينة favorite، رغم أن التأكيد copy-only. | حفظ المفضلة مخفي (`MapDialog.java:233`) وspinner مخفي في read-only (`:205`) والتأكيد ينسخ فقط الإحداثيات الأصلية (`:280-285`). | معاينة مفضلة تستطيع تعديل تفضيل global بصرياً → عرض/نسخ فقط بلا حفظ أو تشغيل أو تغيير map type. |
| دقة الاسم | لم يُثبت عيب إنتاجي في parser: مسار Google `/place/<name>/@lat,lng` محفوظ أصلاً. | أضيف regression في `UiAuditMapSearchLifecycleTest.java:153-167` يثبت label `Riyadh Tower` مع الإحداثيات الدقيقة؛ لم تُغيّر دلالة الاسم بلا عيب مؤكد. | عقد الاسم كان غير محروس → اختبار يمنع regression. |
| LSPatch / مصدر مورد الخريطة | كان `loadMap` يحاول `activity.getAssets()` أولاً ثم classloader، وهو غير صحيح عندما يكون module cache-APK منفصلاً عن أصول host. | `MapDialog.java:447` يستخدم `openMapAsset`؛ `:469-480` يفضّل classloader الموديول ثم host fallback. الاختبار `UiAuditMapSearchLifecycleTest.java:170-186` يثبت المسارين بلا شبكة أو APK. | فشل أو HTML مضيف خاطئ عند تضمين LSPatch → مورد الموديول المستقل له الأولوية، مع fallback متعمد للتشغيل التقليدي. |

## WebView وlint المتبقيان عمداً

لا توجد `@SuppressLint` أو baseline/global ignore أو `newIssues=false` أو تعطيل tests أضيفت في هذا العمل.

* تحذير `SetJavaScriptEnabled` من baseline يبقى **مقصوداً وغير مخفي** في `MapDialog.java:407`: HTML الخريطة المحلي يحتاج JavaScript للتفاعل. الضوابط المرافقة موثقة في `:409-421`: base URL HTTPS، منع file/content access، `MIXED_CONTENT_NEVER_ALLOW`، geolocation مرفوض، وbridge واحد محدود مع WeakReferences وإلغاء lifecycle.
* تحذيرات `SetTextI18n` المتبقية في search/map لا تُخفى. نقل النصوص إلى `strings.xml` ليس ضمن ملكية هذه المهمة (ولا يُسمح بتعديل ملف strings المشترك). لم تتغير رسائل المنتج لتحقيق lint شكلي فقط.
* `@SuppressWarnings("deprecation")` الضيق الموجود في `MapSearchResolver.java:231` يخص مسار `Geocoder` legacy فقط، وليس `SuppressLint` ولا تحذير WebView؛ وهو لازم لدعم minSdk 21 مع مسار API 33 الحديث.

**أثر baseline (46 warning):** لم يُعاد تشغيل lint حسب القيد. في الملفات المملوكة أزيلت أسباب `StaticFieldLeak` (مرجع WebView/Activity قوي)، و`InlinedApi`، و`RtlHardcoded`، وحارستا `ObsoleteSdkInt`. لا يجوز الادعاء بأن العدد الكلي أصبح صفراً من دون تشغيل الأب lint، خصوصاً مع التحذير الأمني WebView والنصوص المتبقية المعلنة أعلاه.

## اختبارات جديدة

الملف الوحيد المضاف: `app/src/test/java/com/wolfox/gps/ui/UiAuditMapSearchLifecycleTest.java`.

| الاختبار | الدليل | ما يغطيه |
|---|---|---|
| `cancellationBeforeMainDelivery_dropsQueuedResolverCallback` | `:70-83` | cancel قبل main callback لا يسلم نتيجة. |
| `dismissDuringLocalResolution_doesNotReviveSearchControls` | `:86-111` | callback محلي آمن بعد dismiss؛ لا يعيد زر confirm. |
| `shortLandscapeAndFontScaleTwo_keepActionsDisjointAndScrollable` | `:114-150` | `640×320` landscape و`fontScale=2`، حدود الأزرار، عدم التراكب، وإتاحة scroll. |
| `googlePlacePath_preservesHumanReadableNameWithExactCoordinates` | `:153-167` | label وإحداثيات Google place path بلا شبكة. |
| `mapAsset_prefersModuleClassLoaderThenUsesBundledAssetFallback` | `:170-186` | أولوية cache/module classloader ثم fallback asset محلي. |

تستدعي `@Before/@After` في `:52-67` تنظيف `MapDialog.dismiss()`، و`ShadowLooper.idleMainLooper()` و`ShadowDialog.reset()` لكل حالة، كي لا تتسرب singletons/callbacks بين اختبارات Robolectric.

**التوقع عند تجميع الأب:** إذا بقيت الـ52 حالة السابقة كما هي، يضيف هذا الملف **5 حالات** ليكون الحد المتوقع **57 حالة ناجحة**. هذا توقع وليس نتيجة منفذة.

## فحص LSPatch الساكن

* لم يُعدَّل `origin` أو config أو توقيع أو APK، ولم يُنفذ أي APK.
* عُثر على نسختين محليتين من `WolFox-GPS-3.1.1-Embedded-Test.apk` وهما byte-identical: SHA-256
  `c713ec951a78ed0b4c7ad67ce45f0d0f496b25c2a9fcedb690aca4be3d257a95`.
* فهرسة ZIP للـAPK فقط تؤكد وجود `AndroidManifest.xml` و`classes.dex` و`assets/wolfox_map.html`.
* لم يكن `/home/ubuntu/wolfox-module-merge/userlspatch.zip` (ولا أي `userlspatch.zip` تحت `/home/ubuntu` حتى عمق 5) موجوداً وقت الفحص؛ لذلك **لم أتحقق مستقلاً** من config المبلغ عنه (`LSPatch v1.2/build487/API102/useManager=false/injectDex=false`) أو من origin داخل الأرشيف. المعلومات الواردة من الأب لم تُغيّر، والتوافق أعلاه مبني على فصل classloader/asset المبلغ عنه وعلى فحص APK المحلي فقط.

## الحدود والحالة النهائية

* لا جهاز Android أو host runtime أو profile LSPatch متاح؛ لا ادعاء بقياس jank/benchmark/phone أو نجاح runtime فعلي.
* لا تعديل لـ GPS/HTML أو تفعيل/انتهاء/visibility/Mock/host eligibility/core SYSTEMUI/self أو root/TestProvider/services/developer/global overlay permissions.
* لا يوجد اختبار شبكة أو credentials أو تنفيذ/تعديل APK طرف ثالث.
* يبقى تشغيل `./gradlew test` وlint وتجميع legacy الكامل للأب مرة واحدة بعد دمج الوكلاء، مع مراجعة تحذيرات baseline المتبقية المعلنة.
