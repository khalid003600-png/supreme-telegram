# التحقق النهائي — WolFox GPS UIAudit

نجح البناء النهائي و**80 حالة اختبار** دون failures أو errors أو skipped. نتيجة Android Lint الخام: **0 أخطاء و3 تحذيرات** بدل 46 تحذيرًا في نسخة Embedded السابقة. لم نستخدم baseline لإخفاء التحذيرات أو SuppressLint شاملًا أو تعطيل اختبارات؛ لا يصح وصف النتيجة بأنها «صفر تحذيرات» أو ضمان خلو كل جهاز من كل الأخطاء.

هذا التسليم يجمع طلب تدقيق الواجهة، وسياسة ظهور الأيقونة عند أول فتح ثم الإخفاء الدائم، والتحقق من عقد ملف LSPatch المرفق. المصدر والموديول جديدان ومنفصلان عن الملفات السابقة.

## ما أُصلح فعليًا

أزيلت مراجع Activity/View القوية من singleton الأيقونة، وأصبحت أوامر attach/position/reveal المؤجلة weak-reference وtoken-bound، وتُلغى عند hide/pause/destroy. يتوفر performClick للنقر القابل للوصول، وتنظف listeners/animations عند فصل الأيقونة. لا يمكن لمؤقت إظهار قديم إلغاء قرار الإخفاء الأحدث.

أزيل الاحتفاظ القوي الثابت بـDialog/WebView في الخريطة. التنظيف يوقف تحميل WebView، يلغي الطلب والجسر والعملاء والمؤقتات، يفصله من الأب ثم يدمره. Resolver يعيد Request قابلًا للإلغاء، ويوقف العمل المتأخر/الاتصالات المملوكة ويمنع نتيجة stale أو مزدوجة بعد تغير البحث أو إغلاق الحوار. إلغاء Future لا يعني ضمان إيقاف فوري لكل تنفيذ Geocoder داخلي تابع للنظام؛ زمنه الفعلي على الجهاز يبقى عاملًا خارجيًا.

تُقيد النوافذ الطويلة بارتفاع قابل للتمرير، مع أزرار مرنة وأهداف لمس 48dp وإغلاق في التذييل، ودعم fontScale2 وشاشات صغيرة/landscape في الحالات المختبرة. أُصلح RTL/LTR وlocale وتوصيفات وصول. بقيت ألوان الأزرار والتباين واسم وإحداثيات الموقع المختار بدقة double محفوظة.

استُبدلت عمليات SharedPreferences.commit الخمس بـapply، بحيث تتغير خريطة الحالة داخل العملية فورًا وتكتب إلى القرص عبر آلية Android. لم تُلغَ بوابة الترخيص أو runtime process marker. أُصلح رفض الترخيص المنتهي بحيث يوقف حالة mock قبل إرجاع الفشل. callbacks التفعيل والسياسة تستخدم weak Activity وفحص lifecycle فلا تعرض حوارًا قديمًا بعد انتهائه.

أزيل fallback الخاص بـActivityThread.currentApplication، وأضيفت حراسة API30 للـgetCurrentLocation قبل تحميل Consumer. أُصلحت أيقونة Manifest وresource table وقواعد fullBackupContent/dataExtractionRules. فُصل namespace عن Manifest المصدر ووُضع native packaging في Gradle؛ Manifest المحلي يولد الهوية وdebuggable=false صراحة. javac المحلي يستخدم --release8 بدل تحذير bootstrap classpath الخاص بـsource/target المنفصلين.

## سياسة الظهور الجديدة

عند أول فتح للمضيف بعد تثبيت نظيف، في غياب مفتاح القرار، `isBubbleVisible()` يعيد true فتظهر الأيقونة فقط. لا يفتح التفعيل أو يبدأ GPS تلقائيًا. الإخفاء الصريح يكتب false، ويظل عبر النشاطات وإعادة تشغيل العملية والتحديث حتى طلب الإظهار. الإظهار الصريح يحفظ true أيضًا حتى الإخفاء التالي.

قيم false القديمة لا تُلغى عند الترقية؛ PID/وقت العملية القديمان لا يحددان الظهور بعد الآن. حارس runtime الخاص بالمحرك ما زال منفصلًا ومقيدًا بالعملية والإصدار. مسح بيانات المضيف/التثبيت النظيف يعيدان الحالة الأولى. لا توجد خدمة تعمل لحظة التثبيت؛ المقصود الظهور عند أول تشغيل للمضيف.

أوامر DirectBootstrap.attach/showControls/hideControls/observeTouch محفوظة، والضغط 850ms محفوظ. لا تُضف showControls تلقائيًا في onResume لأن هذا أمر إظهار صريح سيغيّر اختيار المستخدم. اسم المضيف لم يعد مقصورًا على تطبيق واحد، مع استبعاد النظام/SystemUI والموديول نفسه، ودون أذونات overlay/services/boot جديدة.

## الخط والأصول المنفصلة

تكوين LSPatch المرفق useManager=false وinjectDex=false يبقي APK الموديول منفصلًا عن APK المضيف. الأيقونة وHTML تقرآن من classloader الموديول أولًا. أُصلح نقص خط DIN: تحميله في خلفية مستقلة، بحد 512KiB لكل ملف وكاش content-addressed وAtomicFile، ثم تطبيقه على MainLooper. pending text references ضعيفة ومحدودة؛ لا تحفظ Activity أو View بقوة.

أول إطار قد يستخدم خط النظام إلى أن يجهز DIN؛ هذا مقصود لتجنب القراءة/الكتابة المتزامنة لملف الخط على مسار الواجهة. إذا تعذر الخط، يبقى fallback آمن. قوالب رسائل البحث وزر المحاكاة وخيار Log مفصولة في `assets/wolfox/ui-text-ar.properties` وتقرأ مرة واحدة بحد 8192 بايت، مع العربية البديلة عند دمج مباشر أغفل الملف.

اختبار الخط يميز Typeface fallback عن ملف DIN المحمل مسبقًا: بيانات ملف DIN المتوسط قد تحمل style NORMAL رغم أن الملف المتوسط الصحيح حاضر. تم تصحيح توقع الاختبار، لا إضعاف تحميل الإنتاج أو تعطيل الاختبار.

## تغطية الاختبارات

النتيجة النهائية تشمل 18 مجموعة، بإجمالي 80 حالة:

- الأيقونة والمضيفات: EmbeddedHostFloatingTest 3، FloatingManagerLifecycleTest 12، UiAuditlifecycleStressTest 5، WFHostPolicyTest 4.
- الحالة والمحرك: GPSMockManagerRegressionTest 4، LocalLicenseStateRegressionTest 3، UiAuditstateStorageAndRuntimeTest 6، SelectedLocationStateTest 5.
- الواجهة: ColorAndCloseLayoutTest 4، SearchDialogLayoutTest 3، UiAuditMapSearchLifecycleTest 5، UiAuditdialogsLayoutTest 2، WFDesignButtonTest 6، WolFoxPanelSpoofButtonRegressionTest 4.
- المدخلات والتوافق والأصول: MapLinkParserRegressionTest 4، UiAuditModuleFontAssetsTest 4، UiAuditTextTemplatesTest 3، UiAuditplatformHostRoutingTest 3.

الأزرار المشتركة تشمل API21 و34؛ معظم اختبارات النافذة/المضيف على API34. الحالات تغطي 320/360dp وخطًا مكبرًا حتى 2، وارتفاع landscape، وألوان/تباين/هدف لمس/إغلاق، و12 دورة show-hide، وpause-resume، وإلغاء hold، واستمرار false القديم، وغياب runtime marker، وترخيصًا محليًا منتهيًا، وقراءة الأصول وحدودها وترتيب الاسم والإحداثيات.

لا تنفذ الحالات تفعيلًا بكود إنتاجي أو خادمًا أو APK مضيفًا. قيَم fixture محلية في test source فقط. زمن JUnit في XML زمن محاكاة JVM لا قياس frame time أو FPS أو jank على هاتف.

## التحذيرات الثلاثة التي لم تُخفَ

**OldTargetApi — app/build.gradle:** targetSdk34 محفوظ من مصدر المشروع. رفع الرقم لمجرد إزالة التحذير، دون اختبار سلوك Android الأحدث والمضيف، ليس إصلاح توافق مثبتًا. target المضيف الحقيقي قد يختلف؛ الاختبار على جهازه لازم.

**HardwareIds — LicenseManager:** ANDROID_ID جزء من ربط الترخيص الأصلي. لم نستبدله أو نغير هوية الترخيص لتجميل عداد Lint. يلزم قرار مستقل من صاحب نظام التفعيل إذا أراد تغيير الربط.

**SetJavaScriptEnabled — MapDialog:** HTML الخريطة المحلية يحتاج JavaScript وجسرًا مضبوطًا. التنظيف والقيود على التنقل والوصول والطلب موثقة ومختبرة محليًا، لكن Lint يحذر من تمكين JavaScript بطبيعته. تعطيله سيكسر الخريطة؛ لم نخْف التحذير.

لم تبقَ في تقرير Lint الحالي تحذيرات StaticFieldLeak أو ApplySharedPref أو ClickableViewAccessibility أو InlinedApi أو SetTextI18n أو RtlHardcoded أو PrivateApi/DiscouragedPrivateApi أو ObsoleteSdkInt أو نقص الأيقونة/قواعد النسخ الاحتياطي. هذا نتيجة أدوات التحليل، لا برهان رياضي على استحالة تسرب الذاكرة أو التعطل في كل جهاز.

أدوات البناء قد تعرض إشعارات منفصلة عن Lint: عدم تطابق نسخة XML للـSDK مع AGP8.2.2، وميزات Gradle قديمة بالنسبة إلى Gradle9، وAndroid APIs deprecated لازمة لمسار التوافق القديم. لم تُقدم هذه الرسائل على أنها صفرًا أو تُخفَ. إصلاحات namespace/extractNativeLibs والتحذير المحلي للـbootstrap أُنجزت فعليًا.

## البناء والتغليف

نجح `scripts/verify-qa.sh` كاملًا: tests وLint وتسعة حواجز مصدر، وبناء محلي مقابل XposedAPI82 الرسمي في javac وR8، ثم فحص APK المحمي والتوقيع وعقد DEX.

فحص DEX يحفظ WolFoxHook وDirectBootstrap وتواقيع show/hide/attach/touch العامة، وجسر JavaScript annotation وresolve، وcallback، وعودة Request القابل للإلغاء مع cancel/isCancelled. تحديث الحارس كان لازمًا لأن resolver لم يعد يعيد void؛ لم يُحذف فحص العضو بل توسع لعقد الإلغاء الجديد.

توقيع APK v1/v2/v3 صالح، ZIP سليم، Manifest غير debuggable، icon/resource table موجودة، وقواعد منع النسخ محفوظة. الأصول تطابق بايتات المصدر بما فيها الخطوط والقوالب الجديدة. لا stubs أو Xposed API definitions أو ملفات source أو مفاتيح أو mapping داخل APK. R8 أعاد تسمية **212 فئة داخلية**؛ هذا تشويش أسماء، لا تشفير DEX أو حماية غير قابلة للفك.

## LSPatch 1.2 المرفق

نجح الفحص الساكن `inspect-lspatch-compatibility.py`: config1.2/build487/API102، وجود loader والموديولات المحلية والمعماريات الأربع، نقطة legacy الصحيحة، الأصول المنفصلة والنطاق الفارغ، وغياب اسم الحزمة المقيد القديم من DEX الجديد.

API102 هو مستوى API حديث للإطار، لا سبب لتضمين libxposed في هذا الموديول legacy. مصدر LSPatch يؤكد دعم legacy ويحمل الموديولات المحلية منفصلة. فُحصت هوية origin.apk فقط؛ لم نفك شيفرته أو نغيره أو نعيد تغليفه/توقيعه أو نثبته. لم يتغير sigBypassLevel=0 أو appComponentFactory أو loader/native libraries. التفاصيل والمراجع في `LSPATCH_COMPATIBILITY_AR.md`.

الأرشيف المرفق ما زال يحتوي APK Embedded السابق، وليس APK الجديد. لا يصح القول إن المحتويات الموجودة داخل تطبيق مثبت حدّثت نفسها. التسليم موديول ومصدر مستقلان لا APK طرف ثالث معدل.

## البصمات

```text
Package: com.wolfox.gps
VersionName/VersionCode: 3.1.1 / 313
minSdk/targetSdk: 21 / 34
APK bytes: 648475

APK SHA-256:
586164ea04f3af5b92413d4aa9b5f6c0f454270d0b1b8ec4aa9341047915ea02

Signing certificate SHA-256:
d3c6e8100a52f148cd75cc875a7cb7ea28c8004e45b5bd22b399e119f5e47c96

classes.dex SHA-256:
724e45e7dd4f96e342eebbd33f74af9e8427753810ca339fb2ba77b1cfb495b8
```

APK بتوقيع اختبار جديد، لا مفتاح إصدار المستخدم الأصلي. لا يسمح Android بتحديث نفس الحزمة بشهادة مختلفة؛ استخدم مفتاح الإصدار الأصلي عند بناء تحديث معتمد، ولا تحفظه داخل المصدر.

## حدود النتيجة واختبار الجهاز

لم يُشغّل إطار LSPatch أو APK المضيف أو native libraries أو محرك hooks على هاتف، ولم تُقَس frame times/CPU/heap/jank/TalkBack/IME على OEM حقيقي. مصدر الموديول minSdk21، لكن LSPatch المذكور نفسه يتطلب Android9/API28+ حسب وثائقه. نجاح Robolectric لا يثبت كل جهاز أو إصدار.

قبل اعتماد الإنتاج، اختبر أول فتح نظيف، وعدم فتح التفعيل تلقائيًا، والإخفاء ثم إعادة تشغيل المضيف والتحديث واستعادة false القديم، والضغط850ms، والخط والمفضلة/البحث والاسم والإحداثيات، وإغلاق الخريطة مرات متكررة، والخط المكبّر ولوحة المفاتيح، ثم نجاح/فشل runtime على بيئتك المصرح بها. التزييف لا يبدأ دون ترخيص وموقع وruntime صالحين، وعلامة Mock لا تُخفى.

تقارير `UI_AUDIT_REVIEWS` observations قبل الاختبارات وليست المرجع النهائي للأعداد/السياسة. هذا التقرير ودليل UI_AUDIT_USAGE هما مرجع التسليم الحالي. المصدر يحتوي البناء والأصول والاختبارات وحواجز إعادة التحقق، ويستبعد المفاتيح وmapping وCI والكاش وlocal.properties. نسخ المستخدم السابقة والأرشيفات الأصلية بقيت دون استبدال.
