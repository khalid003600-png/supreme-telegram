# ملخص مراجعة WolFox-GPS-UIAudit

> **حالة هذا المستند:** تلخيص لنتائج المراجعات والإصلاحات المبلّغ عنها فقط. لا يتضمن فحصًا جديدًا لشجرة المصدر، ولا تشغيل Gradle أو Lint أو Robolectric أو بناء APK. لذلك لا يُعد هذا المستند إثباتًا تنفيذيًا لنجاح التجميعة أو الاختبارات أو عمل الواجهة على جهاز حقيقي.

## 1. النطاق وحدود المراجعة

يغطي هذا الملخص وحدات دورة حياة الواجهة والفقاعة العائمة، الخريطة/البحث وWebView، الحوارات وإتاحة الاستخدام، التخزين/الترخيص/التحكم، وتغليف Android ضمن المسار:

`/home/ubuntu/wolfox-module-merge/WolFox-GPS-UIAudit`

كانت التعديلات ضمن الملفات المملوكة التي وردت في النتائج فقط. لم تُجرَ محاولة لإخفاء تحذيرات عبر `SuppressLint` أو `ignore` عام أو تعديل baseline، ولم تُضف صلاحيات root أو overlay أو service أو TestProvider أو شبكة أو بيانات اعتماد.

**حدود مهمة:**

- baseline التاريخي يذكر **46 تحذير Lint** و**52 اختبارًا ناجحًا**، لكن مسار baseline يشير إلى `WolFox-GPS-Embedded` لا شجرة `UIAudit`. لا يمثل ذلك عدّادًا نهائيًا أو تحققًا حديثًا لهذه الشجرة.
- لا توجد في النتائج أي قياسات jank أو استهلاك ذاكرة أو أداء هاتف/IME/TalkBack أو إثبات تشغيل على Xposed/LSPatch أو المضيف الحقيقي.
- لا يجوز استنتاج أن التحذيرات أصبحت صفرًا، أو أن الأداء على الهاتف أصبح مثبتًا، أو أن أية suite نجحت بعد هذه التعديلات.

## 2. الخلاصة التنفيذية

تصف النتائج إصلاحات محلية واسعة لتحسين سلامة دورة حياة الواجهة: إزالة مراجع `Activity`/`View` القوية من مسارات حرجة، إلغاء callbacks مؤجلة عند الإخفاء أو التوقف، منع إظهار تفعيل تلقائي في fallback، وتقوية إلغاء طلبات الخريطة والـWebView. كما تصف تحسينات للتخطيط عند الخط الكبير والشاشات القصيرة، وإصلاحات RTL/locale وإتاحة الاستخدام، وتحويل تخزين واجهة المستخدم إلى `apply()` مع الحفاظ على سياسة إظهار الفقاعة في أول فتح ما لم يخزن المستخدم إخفاءً صريحًا.

وتصف النتائج كذلك تعديلات منصة/تغليف: إزالة مسار reflection لـ`ActivityThread`، إضافة مورد أيقونة وقواعد استخراج البيانات، وربط تجميع الموارد في `build-local.sh`.

هذه **إصلاحات مبلّغ عنها ومراجعة ساكنة**، وليست نتائج منفذة. الفحص الموحد للتكامل وإصدار التقرير النهائي مؤجلان للوالد.

## 3. دورة الحياة والفقاعة العائمة

### المشكلات التي أبلغ عنها

- كان singleton في `FloatingManager` يحتفظ بقوة بـ`Activity` و`View`، بينما قد تبقي runnables الخاصة بالـattach/retry و`View.post` والضغط المطول واجهة قديمة حية أو تنفذ أثراً متأخرًا.
- كان `DirectBootstrap` يلتقط `Activity` في renders مؤجلة (0/650/1800 و0/450ms) دون إلغاء فعلي عند `hide` أو `pause` أو `destroy`، ولم يكن reveal محميًا برقم تسلسلي.
- كان fallback في `ActivityHook` يفتح `ActivationDialog` تلقائيًا عند `onResume` حين تكون الفقاعة ظاهرة والترخيص غير نشط، وهو مخالف لعقد الاكتفاء بإظهار الأيقونة عند أول فتح.

### الإصلاحات المبلّغ عنها

- استخدام `WeakReference<Activity>` و`WeakReference<View>` في `FloatingManager`، ومسح النشاط عند `pause/destroy` وإلغاء animations/listeners عند detach.
- جعل runnables الخاصة بالـattach/retry/position مقيدة بـtoken وذات مراجع ضعيفة.
- تسجيل renders المؤجلة في `DirectBootstrap` وإزالتها بواسطة `renderToken` وقائمة callbacks ضعيفة المرجع؛ وإلغاؤها عند `hide/pause/destroy`.
- حماية reveal المؤجل (850ms) بـ`revealToken` و`removeCallbacks`.
- توثيق وتنفيذ مهلة الضغط المطول 850ms في `CenterLongPressHook` مع token ومالك ضعيف وحارس null للنافذة.
- منع `ActivityHook` من فتح التفعيل تلقائيًا عند `onResume`.
- إزالة SDK guards غير اللازمة تحت `minSdk=21`، وإضافة `AccessibleFloatingButton.performClick()` مع الحفاظ على tap/sحب/snap، وإزالة literal `SetTextI18n` في fallback.

### حد هذا القسم

لا تثبت النتائج أن التسريبات اختفت في runtime، ولا أن جميع السباقات الزمنية قابلة لإعادة الإنتاج أو حُلت على جهاز فعلي. تحذيرات baseline الأخرى لم تُسوَّ إلى صفر، وعداد lint النهائي غير مثبت.

## 4. الخريطة والبحث وWebView

### المشكلات التي أبلغ عنها

- احتفظ `MapDialog` سابقًا بـ`Dialog` و`WebView` ثابتين بقوة، وكان التنظيف المشروط قد يسمح ببقاء WebView قديم عند الاستبدال السريع.
- لم يكن `Resolver` يعيد مقبض إلغاء؛ لذا قد يستمر geocoder أو HTTP أو redirect بعد dismiss أو تعديل النص.
- كان bridge الخريطة يسمح بنتائج بحث stale أو مزدوجة، ولم تُزل مهام تأكيد 220ms عند dismiss.
- كان `SearchDialog` معرضًا للقص في landscape القصير أو `fontScale=2`، مع استخدام مباشر لـTextDirection API 23 رغم `minSdk=21`.
- كان تحميل `wolfox_map.html` يفضّل أصول المضيف بدل مورد classloader للموديول، بما قد يضر توافق LSPatch مع cache-APK منفصل.
- فحص APK الساكن المبلّغ عنه وجد نسختين محليتين متطابقتين من `WolFox-GPS-3.1.1-Embedded-Test.apk` بالـSHA-256:
  `c713ec951a78ed0b4c7ad67ce45f0d0f496b25c2a9fcedb690aca4be3d257a95`
  وتحتويان `assets/wolfox_map.html`.

### الإصلاحات المبلّغ عنها

- استبدال الحقول الثابتة القوية في `MapDialog` بمرجع `Dialog` ضعيف، وإزالة أي مرجع `WebView` ثابت وتنظيف WebView المملوك عند `onDismiss`.
- اعتماد تنظيف شامل للـWebView: إلغاء bridge/الطلب، `stopLoading` و`onPause`، ثم `about:blank`، وإزالة JavaScript interface والعملاء والأب، و`clearHistory` ثم `destroy`؛ مع ترحيل `Preview.destroy` إلى MainLooper عند الحاجة.
- إضافة `MapSearchResolver.Request` قابل للإلغاء، يتضمن إلغاء Future/timeout/اتصالات HTTP وتوسعة short-link قابلة للفصل، وربطه بـ`SearchDialog` و`MapSearchBridge`.
- إضافة generation/active-request للجسر، وحراسة callbacks/تحميل الخريطة من `Activity` أو `WebView` المنتهيين، وإلغاء Runnable التأكيد عند dismiss.
- تقييد `SearchDialog` بارتفاع 90% و`FillViewport`، وإصلاح text direction على API 21 واستخدام RTL START، وإخفاء spinner في favorite للقراءة فقط مع إبقاء النسخ.
- تغيير `openMapAsset` إلى classloader-first ثم host-assets fallback، مع اختبار محلي للمسارين.
- عدم تغيير parser لمسار Google place من دون عيب إنتاجي مثبت؛ أضيف regression لحماية الاسم والإحداثيات.

### حد هذا القسم

يبقى `SetJavaScriptEnabled` مقصودًا لأن HTML الخريطة المحلي يتطلبه؛ لم يُخفَ بتحذير أو suppression، وتُذكر الحواجز الأمنية في التقرير التفصيلي. لا يوجد تشغيل فعلي لطلبات الشبكة أو geocoder أو redirect أو خرائط WebView، ولا تحقق من `userlspatch.zip` إذ لم يكن موجودًا أثناء الفحص المبلّغ عنه.

## 5. الحوارات، التخطيط، RTL وإتاحة الاستخدام

### المشكلات التي أبلغ عنها

- كان زر الإغلاق المساعد 44dp فقط، وبعض أزرار التفعيل/المفضلة/السجل بارتفاعات ثابتة 40–46dp، مع احتمال قص النص أو مخالفة هدف لمس 48dp عند `fontScale=2`.
- استخدمت حوارات التفعيل والاشتراك واللوحة `WRAP_CONTENT`؛ وكانت المفضلة والسجل تمرران القائمة فقط بينما بقي تذييل الإغلاق خارج مسار التمرير، مع خطر القص في 320dp أو landscape.
- احتوى `ActivationDialog` على `Gravity.RIGHT`، و`HistoryDialog` على `SimpleDateFormat` ثابت وإحداثيات `String.format` ضمنية locale، كما دُمجت قيم رقمية مع نصوص مباشرة في الإعدادات.
- توجد ملاحظة توافق خط: `WFTheme.java:57-62` يقرأ `context.getAssets()` فقط؛ لذلك لا يتضمن `hostAssets` بالضرورة أصول الموديول في LSPatch، والسلوك المتوقع هو fallback sans-serif لا ادعاء ظهور DIN.

### الإصلاحات المبلّغ عنها

- رفع `WFDesign.closeButton` إلى 48dp فعليًا مع الحفاظ على الألوان والتباين وRipple.
- تقييد ارتفاع `Activation` و`Subscription` و`WolFoxPanel` بـ86% مع ScrollView، وإضافة `SOFT_INPUT_ADJUST_RESIZE` للحوارات ذات الإدخال.
- إضافة ScrollView خارجي للمفضلة والسجل مع منفذ داخلي محدود للقائمة، واستبدال صفوف أزرار دقيقة بـ`WRAP_CONTENT` حيث يلزم لنمو النص العربي.
- استبدال `Gravity.RIGHT` بـ`Gravity.END`، وتمكين RTL في التفعيل، وتثبيت LTR للإحداثيات/الوقت، واستخدام `Locale.ROOT` للإحداثيات و`Locale.getDefault()` للتاريخ والقيم المتغيرة.
- إضافة أوصاف وصول لحقل التفعيل واسم المفضلة وSeekBars، وجعل بطاقة حالة اللوحة قابلة للتركيز وبحد أدنى 48dp ووصف لإجراء النسخ.

### حد هذا القسم

لا يوجد تحقق TalkBack أو IME أو OEM أو قائمة كبيرة على هاتف حقيقي. لا تزال `SettingsDialog.java:197` تتضمن النص الثابت `Switch#setText("تفعيل الـ Log")` المرشح لـ`SetTextI18n`؛ لم يُستخدم مورد strings أو suppression بسبب قيد موارد الموديول المضمّن. حل تحميل DIN المقترح فقط هو classloader للموديول ثم cache ذري محدود و`Typeface.createFromFile`، ولم يُطبق في مسار الواجهة.

## 6. التخزين والترخيص والتحكم وruntime

### المشكلات التي أبلغ عنها

- وُجدت خمس عمليات `commit()` في `WFStorage` على مسارات واجهة أو حراس حالة قد تحجب UI بالقرص.
- كان `requestVerifiedStart` يعيد `LICENSE_REQUIRED` للترخيص المنتهي قبل مسح علم mock محفوظ.
- كانت مهمة `LicenseManager` و`ToolControlManager` قادرة على الاحتفاظ بـ`Activity` أو تسليم callback/dialog متأخر بعد نهاية دورة الحياة.
- تحذير `HardwareIds` في `LicenseManager` مقصود، إذ إن ربط الترخيص الحالي بـ`ANDROID_ID` مطلوب صراحة.

### الإصلاحات المبلّغ عنها

- استبدال عمليات `commit` الخمس في `WFStorage` بـ`apply` مع توثيق أن خريطة `SharedPreferences` تتحدث فورًا في الذاكرة، وإضافة `synchronized` لإنشاء installation ID كي لا يتكرر داخل العملية.
- تطبيق رؤية مستقلة عن العملية: `isBubbleVisible` تقرأ `prefs.getBoolean(KEY_BUBBLE_VISIBLE, true)`، و`setBubbleVisible` تحفظ true/false صراحة بـ`apply`؛ metadata القديمة الخاصة بـPID/start تُهمل ولا تُحذف.
- إبقاء حارس runtime لـPID/version منفصلًا، وإزالة شرط SDK المتقادم لـ`setElapsedRealtimeNanos` تحت `minSdk=21`.
- إضافة `WeakReference` وفحص usable وتحرير refresh/request guards في `LicenseManager` و`ToolControlManager`، وربط listeners الحوار بمثيل `policyDialog` النهائي لمنع callback/dismiss متأخر.
- إزالة `resolveActivity` لتفادي `QueryPermissionsNeeded`، وبدء custom scheme مباشرة مع HTTPS trusted fallback عند غياب handler.
- معالجة ترتيب `requestVerifiedStart` بحيث تُغلق حالة mock المحفوظة فورًا قبل نتيجة الترخيص المنتهي، بحسب الوصف المبلّغ عنه.

### حد هذا القسم

لا يوجد اتصال activation حقيقي أو اختبار بيانات اعتماد أو runtime مضيف. تحذير `HardwareIds` مقصود ولا ينبغي عرضه كتحذير مُزال. كما يلزم مواءمة اختبارات قديمة تفترض default-hidden أو visibility مرتبطة بـPID/start مع سياسة المستخدم الجديدة.

## 7. منصة Android وتغليف الموارد

### المشكلات التي أبلغ عنها

- استخدم `WolFoxHook` سابقًا `ActivityThread.currentApplication` عبر reflection، وهو مسار private API.
- كان `build-local.sh` يربط Manifest دون compile/link لموارد `res`، ما يجعل إدخال مراجع icon أو data-extraction غير مضمون في APK.
- ظل `minSdk=21` و`targetSdk=34` و`versionName=3.1.1` و`versionCode=313` ثابتة كما هي.

### الإصلاحات المبلّغ عنها

- حذف fallback الخاص بـprivate API في `WolFoxHook` والاعتماد على مسارات `Application/Activity` العامة، مع حارس `Build.VERSION.SDK_INT >= R` وفحص arguments قبل مسار `getCurrentLocation`.
- إضافة `android:icon` لمورد vector محلي وإضافة `dataExtractionRules` مع `allowBackup=false` و`tools:targetApi=s` كحارس Manifest، وإزالة `tools:ignore` و`android:debuggable` الثابتة.
- تحديث `build-local.sh` ليجري preflight لموارد `res` ثم `aapt2 compile` ويربط ملفات `.flat` جميعها عبر `aapt2 link`.
- إضافة vector branding محلي وقواعد تمنع cloud-backup وdevice-transfer لجميع نطاقات البيانات.

### حد هذا القسم

تعذر تأكيد compile/link حقيقي لأن `aapt2` لم يوجد في مسار SDK المتوقع:
`/home/ubuntu/wolfox-module-merge/toolchain/android-sdk/build-tools/34.0.0/aapt2`.

لذلك اقتصر تحقق هذا المسار على XML و`bash -n` والعقود الساكنة كما ورد في النتائج. لا يوجد إثبات تحميل hooks أو عملها على هاتف أو host runtime. كذلك يبقى `OldTargetApi` احتمالًا مقيدًا بعقد `targetSdk=34` ولا يُعرض كتحذير محلول.

## 8. اختبارات مضافة — غير مشغلة

أُبلغ عن إضافة أو تحديث الاختبارات التالية، لكنها **لم تُشغّل** ولا تمثل نتائج نجاح:

| الاختبار | الغرض المبلّغ عنه | حالة التنفيذ |
|---|---|---|
| `UiAuditlifecycleStressTest.java` | خمس حالات: first resume، `bubble_visible=false` المخزنة، إلغاء reveal، 12 دورة show/hide، وpause/resume | مضاف/غير مشغل |
| `FloatingManagerLifecycleTest.java` | تحديث عقد الظهور الافتراضي وإسناد `bubble_visible=false` الصريح للحالات المخفية القديمة | محدّث/غير مشغل |
| `EmbeddedHostFloatingTest.java` | مواءمة عقد الظهور الافتراضي وfalse المخزنة | محدّث/غير مشغل |
| `UiAuditMapSearchLifecycleTest.java` | خمس حالات لـMap/Search/WebView | مضاف/غير مشغل |
| `UiAuditdialogsLayoutTest.java` | تخطيطات 320/360dp و`fontScale=2` مع عزل MainLooper/ShadowDialog/WFStorage؛ دون activation/GPS/network | مضاف/غير مشغل |
| `UiAuditstateStorageAndRuntimeTest.java` | ست حالات: first-open، hide/show عبر reset، metadata قديمة، حدود إحداثيات، ترخيص منتهٍ، runtime marker غائب | مضاف/غير مشغل |
| `UiAuditplatformHostRoutingTest.java` | ثلاث حالات محلية fail-closed مع إعادة singleton/looper؛ دون hook فعلي أو شبكة/credentials | مضاف/غير مشغل |

ورد تقدير غير منفذ لمسار الخريطة: **52 اختبارًا تاريخيًا + 5 اختبارات جديدة = 57** عند تجميعة الوالد. وهو توقع للتخطيط فقط، لا نتيجة اختبار.

## 9. التحذيرات والمخاطر المتبقية

لا توجد نتيجة تثبت أن Lint خالٍ من التحذيرات. بحسب النتائج، لا تزال تحذيرات baseline الأخرى خارج الملكية أو مقيدة بعقود قائمة، ومنها:

- `ApplySharedPref` (5)، `DefaultLocale/ConstantLocale` (2)، `InlinedApi` (1)، `QueryPermissionsNeeded` (1)، `HardwareIds` (1)، `SetJavaScriptEnabled` (1).
- `ObsoleteSdkInt` (5)، `StaticFieldLeak` (2)، `ClickableViewAccessibility` (1)، `SetTextI18n` (18)، `RtlHardcoded` (2).
- تحذيرات مرتبطة بملفات مشتركة مثل `FloatingManager` وواجهات أخرى و`Manifest` و`MapDialog` وhooks، أو تشير إلى شجرة `WolFox-GPS-Embedded` في baseline التاريخي.

هذه الأرقام والأصناف **سياق baseline مبلّغ عنه**، لا ناتج Lint حديث ولا دليل على دقة توزيعها في شجرة UIAudit الحالية. لم تُخف هذه التحذيرات بـsuppression أو baseline أو تعطيل للاختبارات.

## 10. الإخفاقات المبلّغ عنها

**لا توجد إخفاقات تنفيذية مبلّغ عنها** ضمن مدخلات هذه المراجعة. لكن غياب إخفاق مبلّغ عنه لا يعني نجاح البناء أو الاختبارات، لأن التنفيذ لم يحدث أصلًا وفق القيد.

## 11. خطوات التحقق المقترحة للوالد — غير منفذة هنا

1. تشغيل تجميعة موحدة من الشجرة المعتمدة، ثم Robolectric/JUnit كاملة، وتوثيق أرقام الاختبارات الفعلية بدل الاعتماد على baseline أو التقدير 57.
2. تشغيل Lint على المسار الصحيح ومقارنة الناتج بالـbaseline مع توثيق أن baseline التاريخي يشير إلى شجرة أخرى؛ لا تُعلن نتيجة صفر تحذيرات ما لم تثبتها المخرجات.
3. توفير Android SDK Build Tools 34.0.0 أو مسار `aapt2` صحيح ثم التحقق من `build-local.sh`، compile/link للموارد، وAPK الناتج.
4. اختبار تكاملي على المضيف الفعلي وLSPatch/Xposed: دورة حياة النشاط والفقاعة، الإظهار/الإخفاء المحفوظ، تفعيل غير نشط، تحميل `wolfox_map.html` من classloader ثم fallback، وإلغاء search/redirect عند dismiss.
5. إجراء اختبارات هاتف حقيقي عند أحجام 320/360dp وlandscape و`fontScale=2`، مع TalkBack وIME وقوائم كبيرة، وقياس منفصل للأداء/الذاكرة إن كان ذلك مطلوبًا. لا توجد قياسات مثبتة في هذه المراجعة.
6. مراجعة قرار المنتج حول `ANDROID_ID` و`SetJavaScriptEnabled` وخط DIN، مع الاحتفاظ بالتبرير الأمني/التوافقي الصريح بدلاً من suppression العام.

## 12. الملفات والتقارير التفصيلية المبلّغ عنها

التقارير التفصيلية المذكورة في النتائج:

- `reports/ui-audit/lifecycle.md`
- `reports/ui-audit/map-search.md`
- `reports/ui-audit/dialogs.md`
- `reports/ui-audit/state.md`
- `reports/ui-audit/platform.md`

يشمل نطاق التعديلات المبلّغ عنه ملفات دورة الحياة (`DirectBootstrap` وhooks و`FloatingManager`)، واجهات الخريطة/البحث والحوارات، التخزين والمديرين، `WolFoxHook` والـManifest و`build-local.sh` وموارد Android، إضافة إلى ملفات الاختبار المدرجة في قسم الاختبارات. يظل هذا الملخص مرجعًا للاختزال والتسليم، بينما التقرير النهائي للتكامل والتحقق التنفيذي مسؤولية الوالد لاحقًا.
