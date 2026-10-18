# تدقيق دورة حياة واجهة WolFox GPS

**النطاق المنفّذ:** `/home/ubuntu/wolfox-module-merge/WolFox-GPS-UIAudit` فقط.  
**تاريخ التدقيق:** 2026-10-18.  
**الحالة:** أصلحت عيوباً مؤكدة في احتجاز `Activity`/`View`، callbacks المؤجلة، تكرار/إعادة إظهار الفقاعة، `performClick`، وواجهات API المتقادمة. لا يوجد `SuppressLint` أو baseline/global ignore أو تعطيل lint/test أُضيف في هذا العمل.

> لم يُشغَّل Gradle أو lint أو أي test تنفيذي، امتثالاً للتوجيه بأن يجمع الوالد البناء والاختبارات مرة واحدة بعد اكتمال الوكلاء. لذلك ما يلي **تحليل ساكن وتغطية اختبار مكتوبة**، وليس ادعاء نجاح تنفيذ على هاتف أو Sandbox runtime.

## ملخص baseline والنطاق

ملف baseline المعطى يحتوي **46 warning**. من بينها خمسة في `FloatingManager.java` ضمن النطاق المملوك:

| تحذير baseline | الموضع السابق في baseline | المعالجة |
|---|---:|---|
| `StaticFieldLeak` | `FloatingManager.java:40` | أزيل الاحتجاز القوي لـ`Activity` و`View` من singleton، واستبدل بـ`WeakReference` وتنظيف عند `onPause`/`onDestroy`. |
| `ClickableViewAccessibility` | `FloatingManager.java:197` | أضيف `AccessibleFloatingButton.performClick()` ويظل touch listener يستدعي `performClick()` على tap. |
| `ObsoleteSdkInt` (2) | `FloatingManager.java:181,228` | أزيل حارسا `SDK_INT >= 21`؛ `minSdk=21` يجعل `setClipToOutline` متاحاً دائماً. |
| `SetTextI18n` | `FloatingManager.java:186` | لم يعد `setText("WF")` literal؛ fallback يستمد علامة المنتج غير القابلة للترجمة من `CONTROL_TAG.substring(0, 2)`. |

**تنبيه قابلية التتبع:** مسارات العناصر داخل baseline تشير إلى `WolFox-GPS-Embedded` بينما مساحة العمل المعتمدة هنا هي `WolFox-GPS-UIAudit`. لذلك لا يمكن التصريح بعدد lint النهائي لهذه الشجرة قبل تشغيل الوالد. التحذيرات الـ41 المتبقية في baseline خارج الملفات الأربعة المملوكة أو تتبع الشجرة المسماة في baseline، ولم تُخفَ أو تُعطَّل. لا توجد lint issues معروفة متبقية في الملفات التي عدلتها؛ هذه نتيجة مراجعة ساكنة فقط وليست بديلاً عن lint الوالد.

## العيوب المؤكدة والإصلاحات

| المجال | قبل | بعد | دليل المصدر |
|---|---|---|---|
| تسريب singleton | `FloatingManager.INSTANCE` كان يصل إلى `currentActivity` و`floatingButton` القويين. واحتجاز `View` يحتجز `Context`/`Activity`. | كلاهما `WeakReference`، و`onActivityPaused` يفصل الـdecor ثم يمسح النشاط، و`onActivityDestroyed` يفعل الشيء نفسه إن كان النشاط الحالي. | `manager/FloatingManager.java:42-79,100-112,342-358` |
| callbacks الإرفاق | `Handler` retry و`View.post` كانا يلتقطان `Activity`/`ViewGroup`/`View` بقوة حتى انتهاء retry/الإطار التالي. | `scheduleAttach` وretry يحملان `WeakReference<Activity>`؛ runnable التموضع يحمل weak للزر والأب، ويتحقق من `attachToken` وهوية الزر قبل أي رسم. | `FloatingManager.java:255-339` |
| إلغاء تأخير long-press للزر | Runnable الضغط المطول كان يلتقط `view` ثم قد يفتح تأكيد الإخفاء لزر قديم بعد نقل/إزالة. | يحمل weak للزر، ويتحقق من الزر الحالي والنشاط الصالح؛ `detachFromDecorView` يلغي runnable والـanimation وlisteners. | `FloatingManager.java:342-358,360-428` |
| إخفاء/إظهار متسابق | `hide()` يعتمد على token للإزالة، لكن كانت مؤجلات التمهيد و`View.post` مستقلة ويمكن أن تحاول العمل لاحقاً. | يبقى token للإخفاء، وكل attach/retry/تموضع يطابق token؛ الإخفاء يفصل الزر، ويزيل listeners، ويصفّر المرجع. | `FloatingManager.java:125-137,255-358` |
| render المؤجل في bootstrap | lambdas عند 0/650/1800ms و0/450ms كانت تلتقط `Activity` بقوة ولا تُزال صراحة عند pause/destroy/hide. | queue مسجل من `Runnable`ات لا تحمل إلا `WeakReference<Activity>` مع `renderToken` و`renderOwner`; `cancelRenders`/`cancelRendersFor` تزيل callbacks فعلياً عند hide/pause/destroy. | `DirectBootstrap.java:71-126,195-217,239-252` |
| reveal 850ms | إلغاء reveal كان يعتمد فقط على runnable/flag؛ لا token يمنع callback قديم من تنفيذ reveal بعد تسلسل جديد. | token متزايد + weak owner + `removeCallbacks` + clear للـowner في `cancelReveal`; يحتفظ بعقد **850ms**. | `DirectBootstrap.java:129-194` |
| hook الضغط المطول الاحتياطي | التعليق قال 700ms مع تنفيذ 850ms، ولم يكن هناك token؛ `getWindow()` لم يحرس null. | التعليق والتنفيذ **850ms**، token للإلغاء، owner ضعيف وممسوح، وnull guard للنافذة. | `hook/CenterLongPressHook.java:14-80` |
| بداية التطبيق/نافذة التفعيل | مسار fallback في `ActivityHook.onResume` كان يفتح `ActivationDialog` تلقائياً إذا كانت الفقاعة مرئية والترخيص غير نشط. | الاستئناف يهيئ/يراقب ويستعيد الفقاعة فقط؛ لا يفتح activation تلقائياً. فتح اللوحة بالزر يظل يطبق بوابة الترخيص الموجودة. | `hook/ActivityHook.java:52-72`، وبوابة الضغط بقيت في `FloatingManager.java:446-454` |
| `performClick` وإتاحة السحب | زر `FrameLayout` يستهلك touch لكنه لم يoverride `performClick`. | subclass خاص يستدعي `super.performClick()`، ومسار tap ما زال `view.performClick()`؛ السحب وsnap وحفظ الموضع لم يتغيرا من حيث العقد. | `FloatingManager.java:42-49,191-223,400-443` |

## عقد الظهور والحياة المحفوظة

- **fresh install:** تكامل التخزين الذي سلّمه الوكيل المالك يجعل غياب `bubble_visible` يعادل `true`، بينما `false` المخزن صراحة يبقى مخفياً عبر إعادة إنشاء `WFStorage`/بدء عملية جديدة (`util/WFStorage.java:347-357`). لم أعدل ذلك الملف.
- **أول resume:** `FloatingManager.onActivityResumed` يستعيد الظهور المطلوب فقط؛ مع default الجديد يلحق أيقونة واحدة. لا يفتح المسار نفسه حوار activation.
- **إخفاء صريح:** `DirectBootstrap.hideControls` يكتب `false` ثم يلغي reveal/render المؤجل ويفصل الفقاعة (`DirectBootstrap.java:53-62`). لا يعيد delayed callback إظهارها.
- **إظهار صريح:** `showControls`/ضغط الوسط المكتمل بعد **850ms** يمران عبر `FloatingManager.show` ويحفظان `true`، مع بقاء `ToolControlManager.isBlocked` حاجزاً (`DirectBootstrap.java:45-50`, `FloatingManager.java:114-121`). لم يُضف bypass للترخيص أو policy.
- **انتقال النشاط:** pause يفصل وينظف المرجع؛ resume يعيد attach فقط إن كانت التفضيلات مرئية؛ destroy لا يزيل زر نشاط أحدث.
- **المضيف والحدود:** لم تُغيّر فلاتر أهلية المضيف أو guards المصدر العامة، ولا `minSdk=21` أو `targetSdk=34` أو `versionName=3.1.1` أو `versionCode=313`. لم تُضف root أو services أو provider أو global overlay permission أو credentials أو شبكة activation أو تعديل APK خارجي.

## الاختبارات المضافة/المحدّثة

### اختبار جديد مملوك

`app/src/test/java/com/wolfox/gps/manager/UiAuditlifecycleStressTest.java`

تهيئته تنظف `SharedPreferences` المحلي و`WFStorage` singleton وcallbacks الخاصة بـ`DirectBootstrap` وتفصل مدير الواجهة بين كل `@Before` (`:56-94`). يحتوي **5** اختبارات Robolectric محلية:

| الاختبار | الدليل | المتوقع عند تنفيذ الوالد |
|---|---|---|
| fresh default + first resume | `:96-106` | أيقونة واحدة، `bubble_visible=true` من default، ولا `ActivationDialog`. |
| stored false بعد إعادة singleton | `:108-120` | لا تظهر الفقاعة بعد بداية تخزين/استئناف جديد. |
| hide قبل 850ms | `:122-134` | reveal المؤجل لا يعيد الفقاعة ولا يغير `false`. |
| 12 دورات show/hide | `:136-157` | لا duplication ولا resurrection بعد drain إضافي. |
| pause/resume | `:159-176` | `getCurrentActivity()==null` بعد pause، ثم attach واحد عند resume إذا الظهور محفوظ. |

لم يستدع الاختبار `DirectBootstrap.attach()` عمداً لأن attach يبدأ monitoring الإنتاجي؛ الاختبار يوثق أنه لا يستخدم credentials أو شبكة أو خدمة (`UiAuditlifecycleStressTest.java:37-42`).

### تعديلات لازمة لاختبارات قائمة (الاستثناء الذي طلبه الوالد)

- `FloatingManagerLifecycleTest.java:115-199,229-271`: بدّل افتراض default hidden إلى default visible، وجعل حالات hidden القديمة تكتب `bubble_visible=false` أولاً؛ يحافظ ذلك على regression الإخفاء الصريح.
- `EmbeddedHostFloatingTest.java:84-94`: يثبت الآن أن first resume للمضيف المدمج يلحق أيقونة واحدة من default الجديد. `:129-143` يعزل اختبار policy المحجوب بـ`false` صريح كي لا يتغير مقصده.

**التوقع عند التنفيذ:** تستمر الاختبارات الموروثة (كان baseline يذكر 52 ناجحة) مع الاختبارات الجديدة/المحدّثة، ثم يقرر تشغيل الوالد العدد النهائي لأن وكلاء متوازيين أضافوا تغطية أيضاً. لم أدعِ نتيجة تنفيذ.

## حدود ومخاطر متبقية

1. لا يمكن اعتماد عدم وجود lint جديد أو عدّاد suite نهائي دون Gradle/lint الذي سيشغله الوالد؛ لم أشغله حسب القيد.
2. لا يوجد benchmark أو قياس jank/ذاكرة أو اختبار هاتف حقيقي؛ لا توجد بيانات مخترعة عن جهاز أو runtime.
3. lifecycle callback المسجل على `Application` مقصود لعمر العملية؛ callbacks المؤجلة التي تخص نشاطاً أصبحت weak وقابلة للإلغاء. لا توجد آلية unregister لأن التطبيق نفسه يظل مالك callback طوال العملية.
4. `ToolControlManager` و`LicenseManager` ومسارات أهلية المضيف وواجهة activation خارج ملكيتي. لو كشف تشغيل الوالد race داخلها، ينبغي أن يصلح مالكها؛ لم أغيرها إلا إزالة فتح activation التلقائي المؤكد داخل `ActivityHook` المملوك.
5. baseline يحوي تحذيرات خارج النطاق، ولا توجد عملية hide/ignore/SuppressLint لإخفائها. أي warning متبقٍ من تلك المجموعة يجب تقييمه في تدقيق منفصل بمالكه.
