# نتائج التحقق — Embedded Hosts

## النتيجة

نجح `scripts/verify-qa.sh` على المصدر النهائي. نتيجة اختبارات الوحدة والواجهة: **52 tests، 0 failures، 0 errors، 0 skipped**. Android Lint: **0 errors، 46 warnings**، دون تعطيل Lint أو إخفاء التحذيرات بشكل شامل.

## الفروق عن QA2

أزيل ثابت AUTHORIZED_HOST_PACKAGE والفحص المقصور على حزمة واحدة من WolFoxHook. السياسة الجديدة `WFHostPolicy.permits` تقبل أسماء حزم Android صالحة متعددة، وتستثني حزمة Android الأساسية وعائلتها وSystemUI وعائلتها وحزمة الموديول نفسها والمدخلات غير الصالحة.

`scope` في بيانات التحميل أصبح `[]`: لا تلميح ثابت لتطبيق واحد ولا wildcard. هذا لا يحمّل الموديول إلى كل التطبيقات؛ التحميل/الدمج والنطاق الفعلي يعينهما صاحب التطبيق وإطار التحميل. APK النهائي لا يحتوي اسم الحزمة المقيد القديم داخل DEX أو scope.

ActivityHook fallback يُركّب في عملية المضيف المختارة دون افتراض أن اسم العملية يساوي الحزمة. هذا فحص هيكلي لمسار التركيب؛ لم نُجرِ تجربة Android متعددة العمليات على هاتف.

لم نغيّر Manifest أو صلاحياته، DirectBootstrap أو FloatingManager أو الترخيص أو Marker Mock أو تفعيل المحرك أو إعدادات الخط/الأزرار/الموقع من QA2. الأيقونة تبقى داخل Activity، مخفية افتراضيًا، وتتطلب أمرًا صريحًا. لا SYSTEM_ALERT_WINDOW ولا Accessibility service ولا service/receiver/boot جديد.

## الاختبارات الجديدة

| المجموعة | العدد | التغطية |
|---|---:|---|
| WFHostPolicyTest | 4 | أسماء مضيفات متعددة، المدخلات والمسارات غير الصالحة، Android/SystemUI، وحظر حزمة الموديول دون حظر تطبيقات المستخدم الصالحة |
| EmbeddedHostFloatingTest | 3 | البدء المخفي؛ ظهور أيقونة واحدة بأمر صريح ونقلها بين نشاطين بأسماء حزم مختلفة ثم إخفائها واستمرار الإخفاء عبر resume؛ منع الإظهار عند الحظر الإداري المحفوظ |

الاختبارات الـ45 السابقة لخط/ألوان/بحث/إغلاق/موقع/أيقونة/ترخيص محلي/GPS/parsers ما زالت ناجحة. اختبارات الأزرار تتضمن API 21 و34؛ الأيقونة والمضيفات المحلية على API 34.

اختبارات المضيفات تستخدم Activity subclasses مع getPackageName مختلف داخل Robolectric؛ لا تنشئ أو تدمج APKs فعلية ولا تُجري تشغيلًا عبر Xposed/LSPatch. لا تستدعي attach أو API التفعيل أو GPS start أو شبكة في اختبارات المضيفات الجديدة. fixtures التفعيل المحلية السابقة في test source فقط وليست credentials أو إلغاء شروط الإنتاج.

## فحص المصدر وAPK

نجحت تسعة حواجز مصدر، بما فيها `verify_embedded_hosts.py`: غياب قيد التطبيق الواحد، حماية النظام، خفاء افتراضي، تركيب الأيقونة داخل محتوى المضيف، حفظ أوامر attach/show/hide/touch والتفعيل، وعدم توسيع أذونات الظهور.

بُني APK باستخدام Java 17 وSDK 34 وBuild Tools 34.0.0 وXposed API 82 الرسمي في javac وR8. التوقيع v1/v2/v3 ناجح، ZIP سليم، Manifest غير debuggable، والأيقونة والخطوط وHTML تطابق المصدر.

نقاط WolFoxHook وDirectBootstrap وجسر JavaScript وسلسلة callbacks محفوظة. لا Xposed stub definitions أو source/keys/mapping داخل APK. أعاد R8 تسمية **199 فئة داخلية**؛ ليس تشفير DEX ولا ضمانًا ضد الفك. scope فارغ وهوية الموديول وإصداره متطابقان مع المصدر.

## الهوية والبصمات

```text
Package: com.wolfox.gps
Version name/code: 3.1.1 / 313
minSdk/targetSdk: 21 / 34
APK size: 640009 bytes
Signing: fresh test certificate, non-debuggable module

APK SHA-256:
c713ec951a78ed0b4c7ad67ce45f0d0f496b25c2a9fcedb690aca4be3d257a95

Certificate SHA-256:
bea45c243111df90208c35188be4bbd7389e12e10a736768fc8ee93a4dda66bc

classes.dex SHA-256:
eafe20264e63c776cc9f135cb51019a5942a7034200e22872a692654bddeb35c
```

APK محفوظ باسم Embedded منفصل؛ لم يُستبدل تسليم QA2. مفتاح الاختبار ليس مفتاح إصدار المستخدم، فلا يقبل Android تحديث حزمة مثبتة بشهادة مختلفة. استخدم مفتاح إصدارك المعتمد إذا أردت تحديثًا مطابقًا.

## حدود «أي تطبيق»

النسخة تزيل **القيد الاسمي** وتتيح الدمج الداخلي لمشاريعك/تطبيقاتك المصرّح بها، لا ضمان توافق كل تطبيق مغلق المصدر أو كل classloader/OEM/native/isolated process. لا تُظهر الأيقونة في تطبيق لم تُدمج أو تُحمّل فيه الوحدة. النظام/SystemUI والموديول نفسه مستثناة.

لم يُعدّل أو يُدمج أو يُثبّت APK مضيف، ولم يُختبر إطار hooks فعلي أو خادم التفعيل أو هاتف. نجاح العرض المحلي لا يثبت GPS؛ المحرك ما زال يتحقق من runtime والترخيص والموقع ويُبقي Mock ظاهرًا.

التحذيرات الـ46 باقية من توافق APIs/hooks والـlifecycle/strings/RTL/locale وJavaScript/target SDK وملاحظات legacy. لا يُدّعى خلو جميع الأجهزة من كل الأخطاء. راجع `EMBEDDED_HOSTS_AR.md` لربط Activity والأصول وأوامر الإظهار والإخفاء، واختبر دمجك الفعلي بعد تحميل نسخة واحدة واضحة من الموديول.
