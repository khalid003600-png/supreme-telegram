# خطة دمج WolFox GPS — أدنى تغييرات

## النطاق والحدود

هذه **خطة فقط**؛ لا تُنفَّذ فيها أوامر نسخ أو دمج أو بناء أو تفعيل أو اتصال API. النطاق هو مصدر **موديول WolFox وAPK الموديول فقط**. لا تشمل الخطة Companion APK، أو حقن تطبيقات، أو root، أو توسيع نطاق المضيف، أو تجاوز/إبطال التفعيل أو انتهاء الاشتراك.

الثوابت غير القابلة للتنازل:

- يبقى `applicationId`/namespace `com.wolfox.gps` والإصدار `3.1.1`/code `313` ونطاق المضيف المصرح به كما هي في القاعدة.
- التزييف وظيفة runtime فعلية عبر مسارات الموقع الموجودة، ولا تصبح مجرد واجهة.
- تظل المواقع التي ينشئها الموديول **معلَّمة Mock وقابلة للكشف**؛ لا يُعاد أي hook يفرض `isMock()` أو `isFromMockProvider()` إلى `false`، ولا توجد وسيلة بديلة لإخفاء العلامة.
- تبقى بوابة التفعيل، ربط الترخيص بالتثبيت/الجهاز، الفحص الدوري، وحالات `Disabled` و`Expired` و`Reset` نافذة. يظل الترخيص المخزّن صالحاً دون اتصال **إلى تاريخ انتهائه فقط**.

## اختيار القاعدة

**قاعدة الدمج:**

```text
/home/ubuntu/wolfox-module-merge/review/WolFox_GPS_UI_Subscription_Internal_Map_Source
```

**السبب الوظيفي (لا اسم الفرع):** هذه القاعدة هي الوحيدة التي تضم معاً في شجرة واحدة: (1) تزييف Location runtime مع وسم Mock ظاهر وحراسة للكائنات الموسومة، (2) شاشة اشتراك مستقلة وتفاصيل بداية/نهاية/مدة/متبقٍ وفحص يدوي، (3) إيقاف/منع المحاكاة عند انتهاء الترخيص، و(4) معاينة الخريطة الداخلية للمفضلة للقراءة فقط. لذلك تجنب نسخ عشرات ملفات Java المتداخلة من فروع الاشتراك أو الانتهاء أو الشفافية فوق بعضها.

لا تُستخدم `WolFox_GPS_UI_Refresh_Source/wolfox-v311-build` كقاعدة: هي المرجع الأقدم وتحتاج نقل مجموعة وظائف أكبر. ولا تُستخدم فروع `transparent` أو `subscription-update` أو `expiry` كقاعدة: كل منها يغطي جزءاً فقط مما هو موجود في القاعدة المختارة، أو يفتقد خريطة الاشتراك/المفضلة المتكاملة.

## النسخ الفعلي المسموح — Copy overrides

انسخ **هذه الملفات الخمسة فقط** إلى project root للقاعدة المختارة قبل أي build لاحق. هي stubs للتجميع كانت موجودة في المرجع ومفقودة من القاعدة؛ ليست فئات تشغيل تُضمّن في APK.

```text
/home/ubuntu/wolfox-module-merge/review/WolFox_GPS_UI_Refresh_Source/wolfox-v311-build/stubs/de/robv/android/xposed/IXposedHookLoadPackage.java => stubs/de/robv/android/xposed/IXposedHookLoadPackage.java
/home/ubuntu/wolfox-module-merge/review/WolFox_GPS_UI_Refresh_Source/wolfox-v311-build/stubs/de/robv/android/xposed/XC_MethodHook.java => stubs/de/robv/android/xposed/XC_MethodHook.java
/home/ubuntu/wolfox-module-merge/review/WolFox_GPS_UI_Refresh_Source/wolfox-v311-build/stubs/de/robv/android/xposed/XposedBridge.java => stubs/de/robv/android/xposed/XposedBridge.java
/home/ubuntu/wolfox-module-merge/review/WolFox_GPS_UI_Refresh_Source/wolfox-v311-build/stubs/de/robv/android/xposed/XposedHelpers.java => stubs/de/robv/android/xposed/XposedHelpers.java
/home/ubuntu/wolfox-module-merge/review/WolFox_GPS_UI_Refresh_Source/wolfox-v311-build/stubs/de/robv/android/xposed/callbacks/XC_LoadPackage.java => stubs/de/robv/android/xposed/callbacks/XC_LoadPackage.java
```

### فروع لا يُنسخ منها أي ملف

| الفرع | النسخ | المبرر |
|---|---:|---|
| `WolFox_GPS_Transparent_Location_Source/WolFox_GPS_Transparent_Location_Source` | لا شيء | السلوك المطلوب للوسم والإفصاح موجود أصلاً في القاعدة. نسخ `GPSMockManager` أو `WolFoxHook` كاملاً قد يطمس تكامل الاشتراك والخريطة أو يعيد تعارضاً في hooks. |
| `WolFox_GPS_UI_Subscription_Update_Source` | لا شيء | وظائف بيانات الاشتراك وواجهة الإفصاح موجودة في القاعدة، أما النقل الجزئي لملفات UI/التخزين فيزيد التعارض بلا وظيفة مطلوبة إضافية. |
| `WolFox_GPS_Android_Panel_Expiry_Update_Source/WolFox_GPS_Source` | لا شيء | منطق الانتهاء وتفاصيل الاشتراك موجودان في القاعدة؛ الملفان `LicenseManager` و`WFStorage` وحدة مترابطة ولا يُستبدل أحدهما منفرداً. |
| `WolFox_GPS_UI_Refresh_Source/wolfox-v311-build` | stubs الخمسة أعلاه فقط | لا تُستعاد hooks أو ملفات Location منه، لأن المرجع الأقدم قد يحتوي إخفاء علامة Mock وسلوك توافق أوسع غير مطلوب. |

**لا تنسخ** `SHA256SUMS.txt` من أي مصدر، ولا تستخدم `SOURCE_PACKAGE_SHA256SUMS.txt` بديلاً له: كل منهما لا يصف شجرة الدمج النهائية. تُولَّد بصمات جديدة فقط بعد مراجعة وبناء مصرح بهما لاحقاً. لا تنقل مفاتيح توقيع أو أسرار.

## الدمج اليدوي الموضعي المطلوب

### 1) إصلاح `verifyRuntimeInjection` مع الحفاظ على شفافية Mock

**الملف في القاعدة:**
`app/src/main/java/com/wolfox/gps/manager/GPSMockManager.java`

التقرير الساكن يرصد أن `verifyRuntimeInjection()` ينشئ `new Location(...)` غير معلَّم ثم يتوقع من hook تغيير getters، بينما `WolFoxHook` الصحيح لا يبدل الإحداثيات إلا لكائن معلَّم Mock. هذا قد يؤدي إلى فشل تحقق كاذب وإيقاف المحاكاة رغم صحة مسار الحقن.

**hunk المقصود:**

1. استبدل probe الخام غير المعلَّم في `verifyRuntimeInjection()` بكائن ينشأ عبر **نفس مسار الإنتاج** الذي يستدعي `buildLocation(...)`/دالة وسم Location المركزية، أو استدعِ دالة الوسم نفسها على كائن probe قبل فحص getter.
2. اضبط إحداثيات sentinel على الكائن الموسوم، ثم تحقق من مسار getter الفعلي دون تجاوز حراسة `explicitlyMarkedMock` في `WolFoxHook`.
3. إذا تعذر وسم Android القديم بالانعكاس، سجّل/اعرض نتيجة best-effort صادقة ولا تحوّل الكائن غير المعلَّم إلى حالة مسموح بها؛ لا تُخفِ `isMock` ولا توسع أثر hook إلى مواقع حقيقية. يظل الدعم القديم محدوداً بما يتيحه الوسم فعلاً.
4. لا تعدّل أو تحذف حارس الترخيص أو حالة المحاكاة أثناء هذا التحقق؛ يظل التحقق مقيداً بترخيص نشط ومحاكاة مفعلة فقط.

**ممنوع في هذا hunk:** إعادة hooks `isMock()`/`isFromMockProvider()` التي تجبر `false`، أو جعل أي `Location` عشوائي يبدو موسوماً، أو جعل فشل الوسم نجاحاً صامتاً.

### 2) التحقق من اقتران الانتهاء دون استبدال ملفات الترخيص

**الملفات المراد مراجعتها يدوياً كوحدة واحدة في القاعدة، لا نسخها من فرع آخر:**

```text
app/src/main/java/com/wolfox/gps/manager/LicenseManager.java
app/src/main/java/com/wolfox/gps/util/WFStorage.java
app/src/main/java/com/wolfox/gps/manager/GPSMockManager.java
app/src/main/java/com/wolfox/gps/ui/ActivationDialog.java
app/src/main/java/com/wolfox/gps/ui/SettingsDialog.java
app/src/main/java/com/wolfox/gps/ui/WolFoxPanel.java
app/src/main/java/com/wolfox/gps/ui/SubscriptionDialog.java
```

لا يلزم نقل وظيفة غائبة من فرع آخر؛ المطلوب فقط تثبيت الاتصال التالي عند تطبيق الدمج: عند انتقال `WFStorage.isLicenseActive()`/الحالة المركزية إلى `Expired` أو ظهور `Disabled`/`Reset` من التحقق، ينفذ التدفق الموجود إيقاف المحاكاة مرة واحدة، ويمنع إعادة البدء، ويغلق/يعيد واجهات التشغيل إلى التفعيل، مع إبقاء بيانات الربط وسجل التدقيق. تبقى حقول البداية/المدة المعروضة وصفية ولا تصبح مصدراً لقرار السماح؛ إن غابت بيانات موثوقة يعرض UI «غير متاح» بدلاً من اختلاق تاريخ.

### 3) لا دمج يدوي لمسارات التوافق المحذوفة

ابقَ على `WolFoxHook.java` و`ProviderCompatibilityHooks.java` في القاعدة المختارة كما هما بعد إصلاح probe فقط. لا تُستعاد من المرجع تغطية WebView `navigator.geolocation` أو AMap/Baidu أو إجبار `isProviderEnabled`/`isLocationEnabled`/`isLocationAvailable` من دون مراجعة تصميم منفصلة؛ ذلك ليس مطلوباً لهذه الخطة وقد يعيد إخفاء الدلالات أو يخلق كائنات vendor غير معلَّمة. تحافظ القاعدة على مسارات Android/LocationManager وGoogle Fused وHuawei HMS وMapbox المذكورة في التقارير.

## توجيهات التحقق اللاحق (لا تُشغَّل ضمن هذه الخطة)

### A. سلامة المشروع والبناء

1. تحقق بالقراءة أن مسارات stubs الخمسة نُسخت كما أعلاه، وأن Gradle يستخدم Xposed API كتبعـية `compileOnly`/للتجميع فقط ولا يعبئ stub/API داخل APK.
2. ثبت عدم تغيير `AndroidManifest.xml` و`ModuleConfig.java` و`DirectBootstrap.java` و`ActivityHook.java` و`app/src/main/assets/xposed_init`، ولا تغير `com.wolfox.gps` أو 3.1.1/313 أو نطاق المضيف المصرح به.
3. راجع diff النهائي للتأكد من أن النسخ محصور في stubs الخمسة وhunk `verifyRuntimeInjection` المحدد؛ لا تدخل ملفات من Companion APK أو root أو مفاتيح أو تغييرات backend.
4. بعد بناء مصرح به فقط، افحص أن APK الناتج لا يحتوي فئات `de.robv.android.xposed` من الـstubs، ثم أنشئ manifest تجزئات جديداً للنسخة الناتجة بدلاً من نسخ `SHA256SUMS.txt`.

### B. GPS وشفافية Mock

1. مع ترخيص صالح ومحاكاة مفعلة، اختبر LocationManager ثم Google Fused وHuawei HMS وMapbox حيث تكون مدعومة في المضيف؛ يجب أن تصل إحداثيات النقطة المختارة عبر hooks، لا مجرد أن تتغير واجهة اللوحة.
2. تحقق أن المواقع التي أنشأها الموديول موسومة: Android 12+ عبر `Location.setMock(true)`، والإصدارات الأقدم تسجل حدود/فشل best-effort بدقة. لا تدّع ضماناً موحداً عند فشل الانعكاس.
3. تحقق أن `isMock()` و`isFromMockProvider()` لا يعاد تعريفهما إلى `false`، وأن المواقع الحقيقية وغير الموسومة لا تتغير getters الخاصة بها.
4. اختبر `verifyRuntimeInjection()` بعد hunk: يستعمل Location موسوماً من مسار الإنتاج ولا يسبب إيقافاً كاذباً؛ كما لا ينجح باختبار Location حقيقي غير معلَّم.
5. تحقق من ظهور حالة «المحاكاة نشطة/قابلة للكشف» في `WolFoxPanel` والوصف الوصولي في `FloatingManager` عند التشغيل والإيقاف.

### C. الترخيص والانتهاء

1. اختبر تفعيل صالح فقط في بيئة مصرح بها؛ يجب ألا تظهر اللوحة ولا تبدأ المحاكاة قبل `isActive`.
2. اختبر ترخيصاً مخزناً صالحاً مع انقطاع الشبكة قبل النهاية: يستمر فقط حتى timestamp المنتهي المخزن، من دون تمديد محلي.
3. اختبر الوصول للنهاية محلياً، ثم `Disabled` و`Reset` ورفض التحقق: يجب إيقاف المحاكاة فوراً، منع البدء اللاحق، وإعادة تدفق التفعيل؛ لا تُمسح بيانات الربط أو سجل التدقيق تلقائياً.
4. تحقق أن شاشة الاشتراك وSettings/Panel تعرض الحالة والبداية والنهاية والمدة والأيام المتبقية وآخر تحقق؛ وتعرض «غير متاح» عند غياب بيانات الخادم بدلاً من قيمة مختلقة. راقب انحراف ساعة الجهاز كخطر معروف بين التحققات.

### D. UI والخريطة الداخلية

1. افتح `SubscriptionDialog` من نقاط الوصول المقصودة في اللوحة/الإعدادات/التفعيل، ثم أغلقه وتنقل بين الحوارات؛ لا تتكرر اللوحات ولا تُعاد بعد فقد السياق أو انتهاء الترخيص.
2. تأكد من أن تحسن أزرار Search/Map/Favorites/History يبقي الأزرار المعتمدة على نتيجة غير صالحة معطلة، وأن الرجوع/الإغلاق يعيد اللوحة وفق التدفق الحالي فقط حين يكون الترخيص نشطاً.
3. افتح خريطة مفضلة موجودة من `FavoritesDialog`/`MapDialog` و`wolfox_map.html`: يجب أن تسمح بالمشاهدة والتحريك/البحث والنسخ فقط، ولا تحفظ موقعاً ولا تبدأ محاكاة ولا تغير المفضلة.
4. اختبر انتهاء الترخيص أو تعطيله والخريطة مفتوحة: تغلق واجهات التشغيل وتعود للتفعيل، ولا تصبح خريطة المفضلة طريقاً جانبياً لاختيار نقطة أو إعادة تشغيل المحاكاة.

## قرار التنفيذ المختصر

ابدأ من شجرة `WolFox_GPS_UI_Subscription_Internal_Map_Source`، استعد stubs التجميع الخمسة فقط من `wolfox-v311-build`، وطبّق hunk واحداً ضيقاً لتصحيح probe المعلَّم في `GPSMockManager.verifyRuntimeInjection()`. احتفظ ببقية شجرة القاعدة وخصوصاً زوج GPS/hooks، وحدة LicenseManager/WFStorage، وواجهة الاشتراك والخريطة كما هي. لا تنقل أي ملفات وظيفية من الفروع الثلاثة الأخرى، ولا تسترجع توافق المزودين الذي أزيل، ولا تنفذ بناءً أو تفعيلًا ضمن عمل الخطة.
