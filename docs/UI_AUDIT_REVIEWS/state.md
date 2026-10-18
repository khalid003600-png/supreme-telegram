# تقرير تدقيق حالة الواجهة والتخزين والترخيص — WolFox GPS UI Audit

**النطاق المنفّذ:** `WolFox-GPS-UIAudit` فقط، والملفات المملوكة: `WFStorage` و`LicenseManager` و`ToolControlManager` و`GPSMockManager`، مع اختبار مستقل تحت `app/src/test`.

**المرجع:** `baseline-lint.json` يذكر **46 تحذيراً** تاريخياً و**52 اختباراً ناجحاً** قبل هذا التغيير. لم يُشغّل Gradle أو lint أو الاختبارات هنا التزاماً بطلب التجميع المركزي. لذلك لا يدّعي هذا التقرير نتيجة تنفيذ جديدة؛ النتائج أدناه هي مراجعة ساكنة وتوقعات اختبار قابلة للتحقق من الوالد.

## خلاصة القرار

- أزيلت كل استدعاءات `SharedPreferences.commit()` الخمسة من ملفات الإنتاج المملوكة. `apply()` يحدّث خريطة SharedPreferences الموجودة في الذاكرة قبل عودته؛ لذلك يرى حارس الترخيص/الرؤية/runtime القراءة اللاحقة في العملية نفسها فوراً، بينما لا تحجب الواجهة على `fsync` للقرص.
- لم يُستبدل حارس runtime المرتبط بـ PID، ولم تتغير مصادر ربط الترخيص أو `ANDROID_ID` أو خوارزمية البصمة أو سياسات المضيف أو أي سجل حساس.
- وفق طلب المستخدم الأخير، أصبحت الرؤية الافتراضية **ظاهرة عند أول فتح للمضيف المصرح** إذا لم يكن المفتاح موجوداً؛ يبقى `false` المخزن سابقاً مخفياً إلى أن يأتي طلب إظهار صريح. أزيل اعتماد الرؤية وحدها على PID/وقت بدء العملية؛ مفاتيح metadata القديمة تظل على القرص لكنها تُتجاهل عمداً. حارس runtime يظل مستقلاً ومرتبطاً بالعملية.
- النقل الشبكي بقي على منفذ executor أحادي في مديري الترخيص والسياسة؛ لا يوجد اتصال شبكة على main/UI thread.

## الأدلة والإصلاحات

| المنطقة | قبل | بعد / الدليل | الأثر على الواجهة أو الصحة |
|---|---|---|---|
| حفظ الموقع | `WFStorage.java:83-92` كان يستخدم `commit()` (وسجل baseline عند السطر التاريخي 91). | `WFStorage.java:76-95` يوثق خاصية الذاكرة الفورية ثم يستخدم `apply()` في السطر 94. | اختيار موقع، ثم `getLastLocation()` في نفس العملية يقرأ الحالة الجديدة بلا انتظار قرص؛ الإحداثيات غير الصالحة لا تزال مرفوضة في `:81-85` و`:173-175`. |
| حارس mock | `WFStorage.java:180-184` كان `commit()` لحالة التزييف. | `WFStorage.java:183-188` يستخدم `apply()` مع rationale صريح. | لا fsync على مسار زر UI؛ `GPSMockManager`/hook في العملية يريان الحارس فوراً. لا ينشئ ذلك ادعاء تزامن بين عمليات مستقلة. |
| علامة runtime | `WFStorage.java:191-196` كان `commit()`. | `WFStorage.java:194-205` يستخدم `apply()`، مع بقاء شرط PID و`VERSION_CODE` كما هو. | إزالة stall على callback مع الإبقاء على رفض marker قديم من عملية/نسخة أخرى. |
| installation ID | `WFStorage.java:334-338` كان `commit()`. | `WFStorage.java:337-345`: `synchronized` و`apply()`؛ لا تغيير في UUID أو مصدر binding. | طلبان متزامنان في العملية لا ينشئان IDين مختلفين، ولا حجب قرص عند بناء طلب الترخيص. |
| سياسة رؤية الأيقونة (طلب لاحق) | كانت `WFStorage.isBubbleVisible()` في `:347-354` تتطلب تطابق PID ووقت بدء العملية، وكان غياب المفتاح يعني مخفية. | `WFStorage.java:347-357`: `setBubbleVisible()` يحفظ القرار الصريح بـ`apply()`، و`isBubbleVisible()` يقرأ `getBoolean(KEY_BUBBLE_VISIBLE, true)`. | أول open للمضيف يظهر الأيقونة؛ hide صريح يظل hidden بعد restart/update؛ show صريح يظل ظاهراً حتى إن لم تطابق metadata القديمة العملية. لا تُمس مفاتيح metadata المخزنة ولا تعاد كتابة بيانات المستخدم. |
| انتهاء الترخيص وmock | `GPSMockManager.requestVerifiedStart()` كان يعيد `LICENSE_REQUIRED` قبل مسح علم mock محفوظ سابقاً. | `GPSMockManager.java:101-120` يستدعي `stopMocking()` قبل `LICENSE_REQUIRED`، و`:123-140` يفعل ذلك أيضاً عند رفض `startMocking()`. | حالة license منتهية/معطلة تفشل مغلقاً فوراً، بدلاً من انتظار repaint/نداء لاحق إلى `isMocking()`. تظل `StartResult` وشروط التفعيل كما هي. |
| minSdk 21 | تحذير baseline `ObsoleteSdkInt` كان في `GPSMockManager` عند الشرط لـ API 17. | `GPSMockManager.java:273-291` يستدعي `setElapsedRealtimeNanos()` مباشرة في `:284` لأن minSdk=21. تبقى حراسة API 26 و31 اللازمة. | إزالة حراسة متقادمة فقط؛ لا تغيير TestProvider/root/علامة mock المرئية أو المسار العاكس legacy. |
| نتيجة الترخيص المتأخرة | `LicenseManager.refresh()` كان يلتقط Activity، ويحرر `refreshRunning` فقط من callback UI؛ `post()` كان يستدعي `runOnUiThread` بلا فحص انتهاء. | `LicenseManager.java:85-124` يربط owner بـ`WeakReference` ويحرر الحارس في completion على main قبل تسليم callback. `:139-280` يحمل weak Activity إلى المهمة؛ `:293-305` يسلم callback فقط لـ Activity قابلة للاستعمال. | لا callback شبكة إلى Activity finishing/destroyed ولا singleton محجوز في `refreshRunning=true` إذا انتهت دورة الحياة. لا تغيير لحالة الترخيص المخزنة أو قبول الشبكة؛ التحقق المنتهي يظل fail-closed. |
| مهمة policy وحوارها | كانت `ToolControlManager.fetchPolicy(Context, Activity)` تمسك Activity في executor، و`OnShowListener` يستخدم الحقل mutable `dialog`. | `ToolControlManager.java:79-133` يمرر `WeakReference<Activity>` ويصفر `requestRunning` قبل فحص العمر. `:183-214` يربط المستمعين بـ`policyDialog` النهائي ويتحقق من owner/`isShowing()`، و`:224-247` يتحقق من العمر قبل فتح الرابط. | لا تحديث UI أو callback حوار قديم بعد dismiss/destroy؛ لا يسمح dismissal متأخر بمسح dialog أحدث. |
| package visibility | baseline أشار إلى `QueryPermissionsNeeded` في `ToolControlManager` (استدعاء `resolveActivity`). | `ToolControlManager.java:224-247` بدأ custom-scheme مباشرة في `:233` داخل `try`; عند عدم وجود handler يرمي ثم يتابع رابط HTTPS الموثوق كما كان. | لا `<queries>` ولا افتراض إمكانية استعلام PackageManager عن تطبيق المضيف؛ fallback محفوظ. |

## تزامن `apply()` وحدوده

هذا اختيار مقصود وليس suppression lint:

1. `apply()` يعدّل النسخة الذاكرية المشتركة لـ SharedPreferences قبل عودة الدالة؛ لذلك التسلسل مثل `setLocation → getLocation` و`setMocking → isMocking` و`setBubbleVisible → render` لا يرى قيمة قديمة في العملية نفسها.
2. الكتابة الدائمة للقرص مؤجلة، وهو المقصود على مسارات لمس/رسم/حوار الواجهة. لا توجد دعوى benchmark أو jank أو جهاز فعلي في هذا التقرير.
3. لا يعتمد أي تحقق أمني جديد على نجاح كتابة القرص غير المتزامنة: `isLicenseActive()` يستمر في فحص الانتهاء في الذاكرة (`WFStorage.java:228-235`) و`requestVerifiedStart()` يفشل مغلقاً إن غابت علامة runtime/الترخيص (`GPSMockManager.java:101-120`).
4. لا يصلح `commit()` ولا `apply()` وحدهما اتساق SharedPreferences بين عمليات Android مستقلة؛ لذلك أبقينا PID/version guard الخاص بـruntime ولم نخلطه بسياسة الرؤية التي صار مطلوباً صراحة أن تكون مستقلة عن العملية.

## اختبارات مضافة (لم تُنفذ هنا)

الملف: `app/src/test/java/com/wolfox/gps/manager/UiAuditstateStorageAndRuntimeTest.java`

| الاختبار | الأسطر | المتوقع |
|---|---:|---|
| `firstAuthorizedHostOpen_defaultsVisibleWithoutCreatingAnImplicitRecord` | 64-72 | غياب المفتاح يظهر الأيقونة ولا ينشئ قراراً مخفياً/ظاهراً ضمنياً. |
| `explicitHide_survivesStorageSingletonResetAndLegacyProcessMetadata` | 74-86 | `false` المخزن يبقى مخفياً حتى بعد reset singleton ومع PID/start قديمين. |
| `explicitShow_ignoresRetiredProcessIdentityAndRemainsVisibleAfterReset` | 88-101 | show صريح يبقى ظاهراً عبر metadata عملية قديمة/reset. |
| `coordinateBoundariesPersistImmediately_butInvalidJitterSamplesCannotReplaceSelection` | 103-118 | قبول `(-90,-180)` و`(90,180)`؛ رفض تجاوز الحد وNaN وInfinity مع احتفاظ الاختيار السليم. |
| `expiredLicenseStartRequest_clearsPersistedMockFlagWithoutWaitingForAnotherUiRead` | 120-134 | expiry يرجع `LICENSE_REQUIRED` ويمسح mock في الذاكرة والتخزين فوراً. |
| `missingCurrentProcessRuntimeMarker_failsClosedAndDoesNotLeaveMockingDoubled` | 136-149 | غياب marker للعملية الحالية يرجع `RUNTIME_UNAVAILABLE` ولا يترك mock مضاعفاً/مخزناً. |

الـ`@Before` والـ`@After` في `:39-62` يعيدان singleton `WFStorage` وحقول `GPSMockManager` الساكنة/القابلة لإعادة الاستخدام لكل اختبار Robolectric، ولا يستدعيان شبكة أو hook أو provider أو credential حقيقي.

## lint المتبقي والحدود

- **لم يُخف أي تحذير**: لا `SuppressLint` ولا baseline جديد ولا global ignore ولا تعطيل اختبار.
- يتوقع زوال الفئات الملموسة ضمن النطاق: خمسة `ApplySharedPref`، و`ObsoleteSdkInt` الخاص بـ`GPSMockManager`، و`QueryPermissionsNeeded` الخاص بـ`ToolControlManager`. يلزم تأكيد ذلك بواسطة lint المركزي فقط.
- يظل **`HardwareIds`** في `LicenseManager.java:319-332` متعمداً وغير مخفي: ربط الترخيص الحالي يتطلب `Settings.Secure.ANDROID_ID` ضمن SHA-256 مع installation ID/حزمة/brand/device. طُلب صراحة عدم تغيير ID أو source policy؛ لم يُضف أي log للقيمة الخام أو code أو binding.
- تحذيرات baseline الأخرى (مثل `FloatingManager` lifecycle/static leak/obsolete guards، UI i18n، manifest attributes، MapDialog/WebView، hook reflection) تقع خارج الملفات الحصرية لهذه المهمة؛ لم تُعدّل لتفادي تعارض agents. لا يعني ذلك أنها صُححت هنا.
- لا يوجد اختبار هاتف/مضيف Xposed/LSPatch ولا benchmark للأداء ولا اتصال activation حقيقي. لم تُشغّل اختبارات/Gradle؛ على الوالد تشغيل suite الموحد مرة واحدة ثم مقارنة lint بالـbaseline.
- قد تحتاج اختبارات lifecycle القديمة التي كانت تؤكد default-hidden إلى تحديث من agent lifecycle حسب سياسة المستخدم الأخيرة؛ هذا التقرير لا يعدّل اختبارات الغير.

## ثوابت غير معدلة

`minSdk=21`, `targetSdk=34`, `versionName=3.1.1`, و`versionCode=313` بقيت كما هي. لم تُضف أذونات root/TestProvider/services/developer/global overlay، ولم تُعدل APKات طرف ثالث، ولم يُنشأ خادم تفعيل أو credential.
