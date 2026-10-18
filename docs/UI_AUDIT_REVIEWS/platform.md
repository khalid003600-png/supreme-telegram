# تدقيق منصة Android وواجهة WolFox GPS — مراجعة UI/Packaging

**النطاق الزمني:** مراجعة ساكنة للمصدر الموجود في `WolFox-GPS-UIAudit` فقط.  
**المرجع السابق:** `reports/ui-audit/baseline-lint.json` يحتوي **46 تحذيرًا** ونتيجة سابقة معلنة: **52 اختبارًا ناجحًا**.  
**لا ادعاء بتشغيل:** لم يُشغَّل Gradle أو Lint أو Robolectric أو بناء APK أو Xposed/LSPatch في هذه المهمة، التزامًا بطلب أن يجمع الوكيل الأب مرة واحدة بعد اكتمال الوكلاء. لا توجد نتائج جهاز/هاتف، ولا قياسات jank أو benchmark أو إثبات تشغيل hooks.

## ملخص النتيجة

تم إصلاح الأخطاء المؤكدة الواقعة في الملفات المملوكة فقط:

1. إزالة استخدام Android private API (`ActivityThread.currentApplication`) الذي كان سبب تحذيري `PrivateApi` و`DiscouragedPrivateApi`.
2. حماية مسار `LocationManager.getCurrentLocation` العام بحارس API 30 قبل تحميل `java.util.function.Consumer`، مع فحص مدخلات callback.
3. إصلاح `MissingApplicationIcon` بأيقونة Vector محلية حقيقية وربطها بالـManifest.
4. إضافة قواعد `dataExtractionRules` التي تمنع cloud backup وdevice transfer، مع بقاء `allowBackup=false`.
5. إصلاح مسار التغليف المحلي: يجمّع `aapt2` الموارد ويربط ملفات `.flat`، بدل بناء APK بلا موارد رغم وجود مراجع Manifest إليها.
6. إضافة اختبار Robolectric فريد يعزل `MainLooper` والـsingletons، ويتحقق من سياسة host ومن فشل fixture وقت التشغيل بصورة fail-closed ومن تعبئة الأيقونة/قواعد XML — **من دون تثبيت أو محاكاة hook فعلي**.

لم تتغير `minSdk 21` أو `targetSdk 34` أو `versionName 3.1.1` أو `versionCode 313`.

## الأدلة والتغييرات قبل/بعد

| المجال | الدليل قبل | التغيير بعد | الأثر المتوقع |
|---|---|---|---|
| Android private API | تقرير الأساس: `PrivateApi` عند `WolFoxHook.java:133` و`DiscouragedPrivateApi` عند `:134` بسبب reflection لـ`android.app.ActivityThread.currentApplication` | حُذف reflection بالكامل. في `app/src/main/java/com/wolfox/gps/hook/WolFoxHook.java:103-135` بقيت فقط نقطتا `Application.attach` و`Application.onCreate` العامتان؛ التعليق عند `:132-134` يوضح أن البديل المتأخر هو callback `ActivityHook` العام التالي. | لا وصول hidden/private API ولا ادعاء بأن hook نفّذ عند عدم وجود دليل. عند تحميل متأخر، يتهيأ السياق عند دورة Activity عامة لاحقة بدل التفاف داخلي غير مدعوم. |
| توافق API العام | كان تثبيت `getCurrentLocation` يحاول تحميل `java.util.function.Consumer` على كل API رغم أن method هي API 30+. | `WolFoxHook.java:215-249`: حارس `Build.VERSION.SDK_INT >= Build.VERSION_CODES.R` قبل `loadClass`؛ وفحص null/length/type للـargs عند `:231-232`. | لا محاولة لتحميل API/نوع غير متاح قبل Android 11؛ والـcallback المشوّه أو null يقف دون لمس نتيجة المضيف. الاستثناءات الاختيارية ما زالت معزولة بـ`HookSafety`/catch ولا تثبت نجاح hook. |
| أيقونة التطبيق | تقرير الأساس: `MissingApplicationIcon` عند `AndroidManifest.xml:10`. | `AndroidManifest.xml:16` يربط `@drawable/wolfox_module_icon`؛ المورد الجديد `res/drawable/wolfox_module_icon.xml:2-16` Vector محلي (خلفية WolFox، pin، علامة plus) وليس PNG خارجيًا أو APK طرفًا ثالثًا. | يتوفر `android:icon` صريح قابل للتغليف والتدرج مع كثافات Android، دون مورد شبكي أو تعديل APK خارجي. |
| النسخ الاحتياطي واستخراج البيانات | تقرير الأساس: `DataExtractionRules` عند Manifest القديم `:12` بينما `allowBackup=false` فقط. | `AndroidManifest.xml:11-19`: أبقيت `android:allowBackup="false"` وأضيف `android:dataExtractionRules="@xml/ui_audit_data_extraction_rules"` مع `tools:targetApi="s"` كحارس Manifest معلن لـAPI 31+. `res/xml/ui_audit_data_extraction_rules.xml:2-17` يستبعد `root`, `file`, `database`, `sharedpref`, و`external` من cloud backup وdevice transfer. | API 21–30 يحترم `allowBackup=false`؛ API 31+ يحصل أيضًا على قواعد data extraction صريحة. `tools:targetApi` **ليس** `tools:ignore` ولا تعطيل Lint، بل وسم توافق للخاصية التي لا توجد قبل S. |
| خصائص Manifest والبناء | كان `tools:ignore="HardcodedDebugMode"` في Manifest، مع `android:debuggable="false"` ثابتة. وكان `build-local.sh` يربط Manifest بلا `aapt2 compile/link` لمجلد `res`. | أزيل `tools:ignore` و`android:debuggable` من `AndroidManifest.xml:10-20`؛ يحدد Android Gradle نوع debug/release بدل قيمة Manifest ثابتة. في `build-local.sh:45-46` preflight للملفين، و`:125-140` compile للمجلد `res` ثم `-R` لكل `.flat` عند link. | لا suppression عالمي/محلي جديد ولا hardcoded debuggable. سيحتوي APK المحلي على resource table اللازمة للأيقونة وdata rules، أو يفشل preflight بوضوح إذا حُذف مورد. |
| R8/Xposed/stubs | فُحصت العقود دون تغيير غير مبرر: `app/proguard-rules.pro:2-14` يحافظ على `WolFoxHook` و`DirectBootstrap`، ويمنع shrink/optimize؛ و`assets/xposed_init:1` يشير إلى `com.wolfox.gps.hook.WolFoxHook`. `build-local.sh:82-99,118-123` يستعمل official API JAR إن مرّر، أو stubs compile-only كـ`--lib` فقط. | لا تغيير في ProGuard/R8 لأن العقد القائم يقي نقطة الدخول من الفقد ولا يضم stubs إلى APK. | لا minifier loss متوقع لنقطة دخول Xposed أو bridge المباشر؛ لا يوجد اعتماد على Android private API في التغيير الجديد. يلزم تأكيد ذلك في build الأب عبر mapping/APK inspection، لا في هذه المراجعة الساكنة. |

## الاختبار المضاف (متوقع تشغيله لدى الوكيل الأب)

**الملف:** `app/src/test/java/com/wolfox/gps/util/UiAuditplatformHostRoutingTest.java`

| الاختبار | الأسطر | ماذا يثبت | ما لا يثبته عمدًا |
|---|---:|---|---|
| `hostPolicyRoutesOnlyEligibleNonCoreNonModulePackages` | 67-76 | توجيه سياسة المضيف: null و`android*` و`com.android.systemui*` وself مرفوضة؛ host صالح آخر مقبول، بما يتفق مع `WFHostPolicy.java:13-19`. | لا يختار أو يحقن تطبيقات طرف ثالث. |
| `localRuntimeFixtureFailsClosedWithoutVerifiedHookMarker` | 78-89 | fixture ترخيص محلي غير حقيقي مع موقع محلي يظل `RUNTIME_UNAVAILABLE` عند غياب marker، ولا يثبت حالة mocking. | لا يثبت نجاح runtime injection ولا ينفذ hook أو شبكة أو credential. |
| `manifestPackagesVectorIconAndDataExtractionRules` | 91-107 | Resource runtime fixture يحتوي icon non-zero والـVector المتوقع وroot `data-extraction-rules`. | لا يفحص APK النهائي أو جهازًا فعليًا. |

العزل مطلوب ومطبق: `@Before` في `:43-55` يفرغ MainLooper وSharedPreferences ويعيد `WFStorage.instance` و`GPSMockManager.instance`، و`@After` في `:57-65` ينظفها مجددًا. الاختبار يعمل بـRobolectric SDK 34 (`:34-36`) ولا يستدعي `WolFoxHook.handleLoadPackage` أو Xposed/LSPatch.

## التحقق المنفذ هنا

تم تنفيذ تحقق ساكن فقط، وكانت نتائجه ناجحة:

- `bash -n build-local.sh` — صياغة shell سليمة.
- تحليل XML بـPython `xml.etree.ElementTree` للـManifest والأيقونة وملف قواعد الاستخراج — الملفات الثلاثة well-formed.
- فحص نصي للملفات المملوكة: لا `tools:ignore`، ولا `SuppressLint`، ولا `lintOptions`، ولا `disable*lint`، ولا تنفيذ `ActivityThread.currentApplication`. ظهرت عبارة الاسم فقط داخل **تعليق التبرير** في `WolFoxHook.java:132`، لا كاستدعاء.
- تحقق نصي من العقد: `minSdk 21`، `targetSdk 34`، `versionCode 313`، و`versionName 3.1.1` ما زالت في `app/build.gradle:12-16`؛ وتوجد أوامر compile/link للموارد في `build-local.sh:125-140`.

## تحذيرات Lint المتبقية وحدود النطاق

لا يصح الادعاء بـ«صفر تحذيرات» قبل تشغيل Lint حديث. الأساس يحتوي 46 تحذيرًا، وقد عولجت منه ساكنًا أربع حالات مؤكدة: `PrivateApi` و`DiscouragedPrivateApi` و`MissingApplicationIcon` و`DataExtractionRules`. أما العناصر التالية فبقيت خارج الملفات المملوكة أو مقيدة بعقد الإصدار، ولم تُخفَ بـbaseline أو `@SuppressLint` أو تعطيل Lint:

| مجموعة الأساس | العدد | الموقع/الحالة |
|---|---:|---|
| `ApplySharedPref` | 5 | `WFStorage.java` — ملف مشترك خارج ملكية هذه المهمة؛ بعض commits موصوفة كحرجة عبر class-loader/process. يلزم مالك التخزين مراجعة كل موضع، لا تحويلها آليًا إلى `apply()`. |
| `DefaultLocale` و`ConstantLocale` | 2 | `HistoryDialog.java` خارج النطاق. |
| `InlinedApi` | 1 | `SearchDialog.java` خارج النطاق. |
| `QueryPermissionsNeeded` | 1 | `ToolControlManager.java` خارج النطاق؛ لا أضيف `<queries>` بلا حاجة مثبتة. |
| `HardwareIds` | 1 | `LicenseManager.java` خارج النطاق. |
| `SetJavaScriptEnabled` | 1 | `MapDialog.java` خارج النطاق. |
| `ObsoleteSdkInt` | 5 | `FloatingManager.java` و`GPSMockManager.java` و`MapDialog.java`؛ ثلاثة ملفات ليست ضمن ملكية التعديل، حتى إن ظهر أحدها في مسار دعم. |
| `StaticFieldLeak` | 2 | `FloatingManager.java` و`MapDialog.java` خارج النطاق. |
| `ClickableViewAccessibility` | 1 | `FloatingManager.java` خارج النطاق. |
| `SetTextI18n` | 18 | `FloatingManager.java` و`HistoryDialog.java` و`MapDialog.java` و`SearchDialog.java` و`SettingsDialog.java`؛ لا يوجد تعديل لـ`strings.xml` المشترك. |
| `RtlHardcoded` | 2 | `ActivationDialog.java` و`MapDialog.java` خارج النطاق. |
| `OldTargetApi` | 1 | `app/build.gradle:13`: يبقى `targetSdk 34` عمدًا وفق العقد، ولا يجوز رفعه لإسكات Lint. يمكن أن يظهر التحذير إن كان Lint الأحدث يتطلب target أحدث. |
| `UnusedAttribute` التاريخي | 2 | `usesCleartextTraffic` و`extractNativeLibs` في `AndroidManifest.xml:13-14` لا تؤثران قبل API 23؛ وسم `tools:targetApi="s"` هو حارس Manifest صريح للسمات الحديثة، وليس suppression. يجب اختبار API 21/22 على الجهاز إن كانت هذه الطبقات مهمة. |

## حدود ومخاطر متبقية قبل الدمج

1. بيئة هذه المهمة لا تملك `aapt2` في المسار الافتراضي الذي يتوقعه السكربت (`/home/ubuntu/wolfox-module-merge/toolchain/android-sdk/build-tools/34.0.0/aapt2`)، لذلك لم يُنفّذ compile/link للموارد هنا. السكربت يتحقق منه بوضوح قبل البناء؛ على الوكيل الأب/CI استخدام Android Build Tools 34.0.0 كما يوثق `BUILD_REQUIREMENTS.md`.
2. يلزم تنفيذ `./gradlew test lint` أو مسار CI المصرح به، ثم `WOLFOX_ALLOW_TEST_KEYSTORE=1 ./build-local.sh` في بيئة SDK صالحة، وفحص APK الناتج (`aapt dump badging`/resource table، `apksigner verify`، وR8 mapping). هذه **خطة تحقق** لا نتيجة منفذة هنا.
3. لا توجد عملية runtime host أو هاتف أو framework Xposed/LSPatch في sandbox؛ لا يمكن إثبات تحميل hook أو مظهر الواجهة أو أداء الرسم/اللمس. الاختبارات الجديدة متعمدة في إبقاء marker غير موجود لتثبت fail-closed فقط.
4. لم تتغير سياسة الواجهة: تبقى مخفية افتراضيًا، لا تظهر إلا بأمر host صريح أو long-press المعرّف بـ850ms في `DirectBootstrap.java:25,92-147` (ملف غير معدل)، وكل host صالح عدا core/SystemUI/self حسب `WFHostPolicy.java:13-19`. لم يُضف root أو TestProvider أو service أو global-overlay permission أو شبكة activation أو credential.
