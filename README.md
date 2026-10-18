# WolFox GPS — ColorRoles / UIAudit / LSPatch Local

نسخة مدققة مبنية من مصدر Embedded، بموديول `com.wolfox.gps`، الإصدار الداخلي `3.1.1` ورقم `313`. تجمع إصلاحات الواجهة ودورة الحياة والتخزين مع عقد تحميل أصول الموديول في LSPatch المحلي. تسليم مستقل؛ لم تُستبدل APKs السابقة أو تُعدّل حزمة التطبيق الأصلي المرفقة داخل lspatch.zip.

## تحديث ألوان الأزرار

نسخة ColorRoles تغيير مظهر مستقل مبني على UIAudit، دون تغيير المحرك أو الترخيص أو سياسة الأيقونة. البحث أزرق، والحفظ بنفسجي، والنسخ نيلي، والخريطة فيروزي، والتحديد أخضر فاتح، والمفضلة ذهبي، ومسح الحقل برتقالي، والتزييف أحمر، والإيقاف أحمر عميق، والإغلاق/الإلغاء رمادي. أزرار الاشتراك والتحقق وإعادة التفعيل مختلفة، وأيقونات التعديل والحذف أصبحت لها خلفيات ملونة.

يختار المكوّن النص الداكن أو الأبيض حسب التباين؛ لا يُفرض الأبيض على الخلفيات الفاتحة. التعطيل لا يتيح النقر، ويُظهر سطحًا خافتًا مع حافة تدل على اللون الأصلي. اللون لا يستبدل تسمية الزر أو نتيجة النقر. لم تتغير مواضع الإغلاق السفلي أو التخطيط أو أسماء المواقع المختارة.

[نتائج تحقق نسخة الألوان ودليلها](docs/COLOR_ROLES_VALIDATION_AR.md). أرقام الاختبارات في الأقسام التاريخية أدناه تخص UIAudit قبل تحديث الألوان؛ المرجع النهائي لهذه النسخة هو تقرير ColorRoles.

## سياسة الأيقونة الحالية

**تظهر عند أول فتح للمضيف بعد تثبيت نظيف إذا لم يوجد قرار مخزن.** لا تفتح شاشة التفعيل أو تشغّل GPS تلقائيًا. بعد الإخفاء تحفظ false وتبقى مخفية عبر تبديل الشاشات وإعادة تشغيل العملية والتحديث، حتى أمر الإظهار. القرار القديم false يبقى محفوظًا عند الترقية.

```java
com.wolfox.gps.DirectBootstrap.showControls(activity);
com.wolfox.gps.DirectBootstrap.hideControls(activity);
```

الإظهار اليدوي متاح بضغط منتصف الشاشة 850ms. لا تستدعِ showControls من onResume؛ دع attach/دورة الحياة يستعيدان القرار المخزن. الأيقونة داخل Activity المضيف فقط، لا فوق التطبيقات غير المدمجة، ودون SYSTEM_ALERT_WINDOW/Accessibility/service/boot/Root/TestProvider.

[دليل التشغيل والدمج والسلوك الجديد](docs/UI_AUDIT_USAGE_AR.md).

## الإصلاحات

أزيلت المراجع القوية لـActivity/View من singleton الأيقونة، وأصبحت مهام العرض والإظهار token-bound قابلة للإلغاء؛ hide/pause/destroy تلغي callback قديمًا. تنظيف WebView والجسر والعملاء والطلبات صار مرتبطًا بالنافذة المالكة. البحث يعيد Request قابلًا للإلغاء ويمنع stale results.

أُصلحت مساحات اللمس والتمرير وأزرار الخط المكبّر والإغلاق السفلي وRTL/LTR، وبقيت ألوان الأزرار والتباين وحفظ اسم/إحداثيات الموقع بدقة double. SharedPreferences تستخدم apply مع حفظ الحالة فورًا في الذاكرة، دون كتابة قرص متزامنة على مسار النقر. التفعيل والانتهاء وMock الظاهر وحارس runtime محفوظة.

خط DIN يحمّل في thread خلفي من classloader الموديول إلى كاش محدود وذري؛ أول إطار يستعمل خط النظام إلى أن يجهز DIN ثم يُطبّق على MainLooper. نصوص نتيجة البحث وبعض الأزرار قوالب عربية داخل assets وليس IDs موارد المضيف.

## الاختبارات والتحذيرات

نجحت **80 حالة اختبار** دون failures/errors/skipped. Lint: **0 أخطاء و3 تحذيرات** بدل 46. التحذيرات الباقية معلنة لا مخفية: OldTargetApi لأن target34 لم يُرفع دون تجربة، HardwareIds لأن ANDROID_ID جزء من ربط الترخيص الأصلي، SetJavaScriptEnabled لأن الخريطة المحلية تحتاج JavaScript.

اختبارات الواجهة على API21/34، مع حالات خط مكبّر حتى fontScale2 وشاشة صغيرة/landscape ودورة إظهار/إخفاء متكررة وإلغاء طلبات وتحقق حدود الأصول. هذه محاكاة Robolectric، ليست قياسات FPS/jank أو دليل تشغيل هاتف/Xposed/LSPatch أو ترخيص إنتاجي. [نتائج البناء والتغليف والبصمات](docs/UI_AUDIT_VALIDATION_AR.md).

تقارير `docs/UI_AUDIT_REVIEWS` ملاحظات مراجعة قبل تنفيذ الاختبارات النهائية، وقد تشير إلى العدد القديم أو نواقص عولجت لاحقًا؛ المرجع النهائي هو UI_AUDIT_VALIDATION ودليل الاستخدام الحاليان، لا ملفات QA2/EMBEDDED التاريخية.

## LSPatch المرفق

الملف المرفق إعدادات LSPatch1.2/build487/API102 مع useManager=false وinjectDex=false وأصول الموديول السابق، لا manager.apk ولا APK مُثبّت مستقل. legacy Xposed مدعوم وفق مصدر LSPatch؛ أبقي هذا الموديول على legacy API82 compile-only، مع نقطة xposed_init وتواقيع DirectBootstrap وجسر JavaScript المحفوظة.

الأيقونة/HTML/الخطوط/قوالب النص تقرأ من أصول الموديول نفسه. لا تُعدّل origin.apk أو loader.dex أو native libraries أو appComponentFactory أو sigBypassLevel. [فحص الملف والمراجع وحدود التوافق](docs/LSPATCH_COMPATIBILITY_AR.md).

## البناء وإعادة التحقق

Java17 وAndroidSDK34 وBuildTools34.0.0 وXposedAPI82 الرسمي:

```bash
export JAVA_HOME=/path/to/java17
export ANDROID_SDK_ROOT=/path/to/android-sdk
export ANDROID_BUILD_TOOLS="$ANDROID_SDK_ROOT/build-tools/34.0.0"
export XPOSED_API_JAR=/path/to/official/xposed-api-82.jar
export WOLFOX_ALLOW_TEST_KEYSTORE=1 # اختبار فقط
bash scripts/verify-qa.sh
```

APK بتوقيع اختبار جديد؛ تحديث نسخة مثبتة يحتاج مفتاح إصدارك الأصلي. لا يُوزّع keystore أو mapping أو local.properties أو CI أو بيانات تفعيل. framework LSPatch المذكور يتطلب Android9/API28+ حتى لو كان minSdk الموديول21. تثبيت APK الموديول وحده لا يدمجه في التطبيقات ولا يغيّر GPS للنظام؛ يلزم تكامل/تحميل مصرح به وruntime وترخيص صالح واستجابة فعلية للمحرك.
