# مراجعة التغليف الثابتة — QA2

**الحالة:** أُنجزت مراجعة ثابتة واختبار عقد التغليف على الـAPK الاختباري المتاح مسبقًا، **من دون تشغيل build أو تثبيت/فتح APK أو تشغيل root/LSPatch أو أي API**. لا تعني النتيجة خلو المشروع من جميع الأخطاء ولا تضمن سلوك الجهاز أو التطبيق المضيف.

## النطاق والدليل المفحوص

- راجعت شجرة `QA2` المصدرية وإعدادات R8 وManifest ومسار التغليف. أمّا دليل APK فهو ناتج الأصل المتاح مسبقًا؛ لذا لا يمثل بديلاً عن تشغيل الاختبار بعد أول build جديد لـQA2.
- فُحص APK الاختباري الموقّع الموجود أصلًا (`WolFox_GPS_Module_v3.1.1_test_SIGNED.apk`) قراءةً فقط. بصمته SHA-256 هي `2f91360c17cb76213a5e574c1a669469b712ff5dacf88969efcbbcaacad616bd`.
- لم تُعد مراجعة الأرشيفات الخمسة السابقة. ولم يُنسخ APK أو ملف R8 الخاص إلى QA2.
- لا يعرض هذا التقرير محتوى ملف R8 أو أسماءه المموهة أو موقعه الخاص، ولا مفاتيح أو بيانات توقيع.

## نتائج الدليل الملموس

| بند العقد | النتيجة الثابتة على الـAPK المتاح |
|---|---|
| سلامة وتوقيع APK | بنية ZIP سليمة؛ نجح تحقق `apksigner` باستخدام v1 وv2 وv3. |
| Manifest المترجم | القيمة الفعلية لـ`android:debuggable` هي `false`، وبيانات Xposed موجودة. |
| نطاق الهدف وXposed entry | أصل `xposed_init` داخل APK مطابق للمصدر ويشير إلى `com.wolfox.gps.hook.WolFoxHook`؛ وبيانات LSPatch تحتفظ بالنطاق الوحيد `com.t2.AvailoHader`. |
| DEX entry points | فئتا `WolFoxHook` و`DirectBootstrap` موجودتان بالاسم الخارجي؛ وتواقيع `showControls(Activity)` و`hideControls(Activity)` و`attach(Activity)` و`observeTouch(Activity, MotionEvent)` ما زالت `public static` كما يلزم. |
| JavaScript والجسر | توجد `resolve(String)` عامة في `MapSearchBridge`، ويظهر تعليق وقت التشغيل `@JavascriptInterface` في DEX؛ كما تبقى سلسلة `MapSearchBridge → MapSearchResolver → Callback → Result` ذات التواقيع المتوافقة في DEX بعد R8. |
| Xposed API | لا توجد تعريفات فئات `de.robv.android.xposed.*` داخل DEX؛ هي مراجع خارجية فقط. |
| الأصول المطلوبة | طابقت بايتات PNG للأيقونة وHTML للخريطة والخطين العربيين مصدر QA2 حرفيًا. |
| تسرب ملفات البناء | لم تظهر ملفات مصدر أو mapping أو key/keystore أو مواد توقيع خاصة في APK. وسجلات `META-INF` اللازمة لتوقيع v1 ليست مفاتيح خاصة ولا تُعامل كتسرب. |
| التشويش | تحقّق من تغيير أسماء **198** فئة داخلية (ومنها `GPSMockManager`) عبر R8، مع بقاء مداخل الواجهة المطلوبة. هذا **تشويش أسماء فقط، وليس تشفير DEX كاملًا**. |

## مراجعة المصدر وإبقاء الواجهات

- `app/proguard-rules.pro:2-9` يحفظ اسمَي مداخل Xposed/Direct Bootstrap وسمات التعليقات وأعضاء `@JavascriptInterface`، بينما تسمح القواعد بتغيير أسماء الداخل.
- `app/src/main/java/com/wolfox/gps/DirectBootstrap.java:39-64,93-147` يحتوي الواجهات العامة الأربع المطلوبة بالتواقيع المعتمدة؛ وقد أكد DEX المترجم بقاءها لا مجرد وجودها في المصدر.
- `app/src/main/java/com/wolfox/gps/ui/MapDialog.java:479-511` يعرّض `MapSearchBridge.resolve(String)` بالتعليق المطلوب ويرسل النتيجة إلى resolver.
- `app/src/main/java/com/wolfox/gps/util/MapSearchResolver.java:44-113` يعرّف `Callback.onComplete(Result)` ويضمن مسار callback للفشل والنجاح؛ وأكد اختبار DEX بقاء التواقيع المترجمة بعد تغيير الأسماء الداخلية.
- `app/src/main/AndroidManifest.xml:10-26` يعلن `debuggable="false"`، لكن الاختبار الجديد لا يكتفي بالمصدر: يفحص الـmanifest الفعلي داخل APK.
- لم تظهر من الدليل الثابت مشكلة محددة في class-loader أو reflection أو قاعدة keep تستلزم hunk إصلاح. هذه ليست مصادقة لتشغيل LSPatch على جهاز؛ إن ظهرت مشكلة وقت التشغيل فيجب تسجيل stack trace وبيئة المضيف قبل اقتراح تغيير.

## توافق Xposed API 82

راجعت استخدامات Xposed في QA2 مقابل `xposed-api-82.jar` الرسمي المتاح محليًا. سطح API المستدعى — بما في ذلك `IXposedHookLoadPackage.handleLoadPackage` و`XposedBridge.hookAllMethods` و`XposedHelpers.findClass` وoverloads الخاصة بـ`findAndHookMethod` مع قيمة الإرجاع `XC_MethodHook.Unhook` — موجود في jar API 82.

يدعم مسار البناء الحالي `XPOSED_API_JAR` لاستخدام jar الرسمي كـcompile-only وR8 library، مع fallback للـstubs القديمة. اختبار التغليف الجديد **لا يفترض** وجود `xposed-stubs.jar` بعد build؛ معيار الحزمة هو غياب تعريفات stubs من DEX، لا وجود jar وسيط.

## الاختبار الجديد بعد أي build لاحق

الملف المضاف هو [`tests/verify_packaging_contract.py`](../tests/verify_packaging_contract.py). لا يبني شيئًا ولا يعدّل APK أو mapping أو المشروع. الجذر محسوب من `__file__`، والحالة العادية تبحث عن APK الموقّع ومخرجات R8 في بنية `build/local` المتعارف عليها.

```bash
python3 tests/verify_packaging_contract.py
```

ويمكن تمرير APK موقّع اختياري يقع تحت `build/local/apk`؛ عندئذ فقط يستخدم الاختبار mapping الشقيق داخل نفس بنية `build/local`، ولا يطبع محتواه أو موقعه.

```bash
python3 tests/verify_packaging_contract.py "$APK"
```

يبحث الاختبار عن أدوات Android من `ANDROID_BUILD_TOOLS` أو `ANDROID_SDK_ROOT`/`ANDROID_HOME` أو مواضع SDK القياسية تحت مجلد المستخدم؛ لا يثبت مسار SDK خاصًا بالمشروع. وهو يطبع حالات PASS وعدد الفئات الداخلية المسمّاة فقط، ولا يطبع أسماء R8 المموهة أو مسارات خاصة أو بيانات شهادة.

## حدود النتيجة وخطوة القبول

- لا يوجد هنا اختبار جهاز/محاكي، تحميل LSPatch/LSPosed، تفاعل WebView، أو اختبار التطبيق المضيف الفعلي؛ ولذلك لا يمكن الجزم بتوافق class loader أو انعكاس المضيف أو اللمس/واجهة المستخدم في الواقع.
- لا يمكن لـR8 أو هذا الفحص ادعاء «حماية غير قابلة للكسر»: DEX والأصول المقروءة داخل APK قابلة للاستخراج، وR8 لا يشفّرها.
- قبول الإصدار يستلزم بعد البناء تشغيل الاختبار أعلاه ثم اختبار يدوي موثق على جهاز مصرح به للتأكد من تحميل Xposed، استدعاء مداخل `DirectBootstrap`، bridge البحث، وسير عمل الواجهة. 
