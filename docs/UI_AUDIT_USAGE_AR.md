# WolFox GPS — واجهة مدققة وتوافق تحميل LSPatch المحلي

## الأيقونة عند أول فتح والإخفاء الدائم

عند أول فتح للتطبيق المضيف المدمج، إذا لم يوجد قرار ظهور/إخفاء محفوظ، **تظهر الأيقونة تلقائيًا داخل Activity المضيف**. ظهور الأيقونة لا يفتح شاشة التفعيل تلقائيًا، ولا يبدأ تزييف الموقع.

عندما تختار إخفاء الأيقونة، يحفظ الموديول `bubble_visible=false` ويبقى هذا القرار عبر انتقال Activity وإعادة تشغيل العملية وتحديث التطبيق. لا تعود الأيقونة إلا عند طلب الإظهار. قرار الإظهار الصريح يبقى أيضًا محفوظًا حتى تختار إخفاءها مرة أخرى. رقم العملية أو وقت بدئها لا يبطل هذا القرار.

المستخدمون الذين أخفوا الأيقونة في نسخة قديمة يحتفظون بإخفائهم؛ تحديث الموديول لا يلغي قيمة false القديمة. مسح بيانات المضيف أو إعادة تثبيت نظيفة يبدأ حالة جديدة، فيظهر الرمز عند أول فتح مجددًا.

هذا السلوك يحدث عند **أول تشغيل للمضيف بعد التثبيت**، وليس لحظة نسخ APK أو تثبيته دون فتحه. APK الموديول ليس Launcher مستقلًا، ولا توجد خدمة بدء أو Overlay عامة.

الإظهار يدويًا بالضغط 850ms في منتصف الشاشة أو بالأمر:

```java
com.wolfox.gps.DirectBootstrap.showControls(activity);
```

الإخفاء من خيار اللوحة/تأكيد الضغط المطوّل على الأيقونة، أو:

```java
com.wolfox.gps.DirectBootstrap.hideControls(activity);
```

الطلبات المؤجلة للظهور والعرض تُلغى عند الإخفاء أو pause/destroy؛ لا يجوز أن يعيد callback قديم إظهارها بعد قرارك الجديد. التفعيل والانتهاء والحظر الإداري لا تُلغى؛ النقر على الأيقونة يفتح اللوحة أو التفعيل وفق حالة الترخيص.

## دمج المضيف

أُبقي دعم أسماء تطبيقات مختلفة، دون شرط اسم الحزمة القديم. الأيقونة View داخل محتوى Activity، لا فوق تطبيقات غير مدمجة؛ لا SYSTEM_ALERT_WINDOW أو Accessibility service أو Root أو TestProvider أو تشغيل تلقائي عند الإقلاع. النظام الأساسي وSystemUI والموديول نفسه مستثناة.

في مصدر تطبيقك المصرّح به، اربط Activity بعد تهيئة شاشتها:

```java
@Override
protected void onCreate(android.os.Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // جهّز محتوى الشاشة المعتاد قبل الربط.
    com.wolfox.gps.DirectBootstrap.attach(this);
}

@Override
public boolean dispatchTouchEvent(android.view.MotionEvent event) {
    com.wolfox.gps.DirectBootstrap.observeTouch(this, event);
    return super.dispatchTouchEvent(event);
}
```

لا تستدعِ `showControls` من onResume أو مؤقت مخصص؛ ذلك سيتجاوز قرار الإخفاء لأن الأمر نفسه طلب إظهار صريح. دع `attach` ودورة الحياة يستعيدان القرار المحفوظ. اختر تحميل نسخة واحدة واضحة من الموديول، لا نسختين داخل classloaders مستقلة.

إذا كان framework يحمّل الموديول وActivityHook، فإن fallback موجود أيضًا عند اختلاف اسم العملية عن اسم حزمة المضيف. توفر المصدر أو APK لا يحمّله تلقائيًا في أي تطبيق آخر.

## تحسينات الواجهة

أزيلت مراجع Activity/View القوية من singleton الأيقونة، وفُصلت مهام التأخير عن Activity عبر weak references ورموز إلغاء. تحسنت إتاحة النقر عبر performClick، وتنظف listeners/animation عند فصل الأيقونة.

البحث يمتلك Request قابلًا للإلغاء، ويرفض callback قديمًا بعد تغير الاستعلام أو إغلاق الصفحة. الخريطة تنظف WebView والجسر والتحميل والعملاء والمؤقتات عند الإغلاق، ولا تحتفظ به في حقل ثابت قوي. استعراض المفضلة يبقى قراءة/نسخًا فقط دون تشغيل GPS جانبي.

واجهات التفعيل والاشتراك والمفضلة والسجل واللوحة قابلة للتمرير ضمن ارتفاع محدد، مع أهداف لمس 48dp وارتفاعات مرنة للأزرار. زر الإغلاق أسفل المحتوى، والعنوان مستقل. الإحداثيات باتجاه LTR، والأسماء العربية لا تُعكس. الألوان الوظيفية والتباين محفوظان.

خط DIN العربي يُحمّل من أصول الموديول في خلفية منفصلة، بكاش محتوى محدد الحجم وكتابة ذرية؛ أول إطار يستخدم خط النظام حتى يجهز DIN ثم تُحدّث النصوص على MainLooper. لا تُقرأ أو تُكتب ملفات الخط على مسار setText/رسم الواجهة. إذا غابت الأصول أو تلفت، يبقى خط النظام البديل بدل تعطل الواجهة.

قوالب الرسائل العربية منفصلة في `assets/wolfox/ui-text-ar.properties`، ومخزنة مرة واحدة بعد قراءة صغيرة محدودة الحجم. هذا يجعل نصوص البحث والزر تعمل دون IDs موارد قد لا توجد في APK المضيف. أضيفت أيقونة Manifest وresource table وقواعد منع النسخ الاحتياطي إلى بناء الموديول نفسه.

## توافق LSPatch المرفق

الفحص الساكن لـlspatch.zip وجد LSPatch 1.2/build 487/API 102 مع `useManager=false` و`injectDex=false`. أبقي الموديول legacy Xposed؛ وثائق LSPatch تدعم هذا المسار، فلا يلزم تحويله إلى libxposed لمجرد أن الإطار يذكر API 102.

أيقونة PNG وHTML والخطوط والقوالب تقرأ من classloader الموديول أولًا، مع fallback دمج مباشر حيث يلزم. لا يعتمد التحديث على private ActivityThread API أو legacy resource hooks فقط للوصول إلى الأصول.

الأرشيف يحتوي نسخة Embedded السابقة، وليس APK الجديد. لم يُستبدل APK داخله ولم يُعدّل origin.apk أو loader.dex أو native libraries أو appComponentFactory أو sigBypassLevel. التسليم هو **موديول ومصدر جديدان**، وليس نسخة معاد تغليفها من تطبيق طرف ثالث. راجع `LSPATCH_COMPATIBILITY_AR.md` للأدلة والمراجع وحدود التوافق.

الموديول minSdk21؛ إطار LSPatch المذكور نفسه يتطلب Android9/API28 أو أحدث وفق وثائقه. اختبار Robolectric على API21/34 ليس إثبات تشغيل LSPatch على API21، ولا إثبات توافق كل إصدار Android أو مصنع.

## بناء وإعادة فحص

المتطلبات Java17 وSDK34 وBuildTools34.0.0 وXposedAPI82 اعتماد تجميع فقط:

```bash
export JAVA_HOME=/path/to/java17
export ANDROID_SDK_ROOT=/path/to/android-sdk
export ANDROID_BUILD_TOOLS="$ANDROID_SDK_ROOT/build-tools/34.0.0"
export XPOSED_API_JAR=/path/to/official/xposed-api-82.jar
export WOLFOX_ALLOW_TEST_KEYSTORE=1  # اختبار فقط
bash scripts/verify-qa.sh
```

هوية الموديول `com.wolfox.gps` وإصداره الداخلي 3.1.1/313 محفوظان. APK بتوقيع اختبار جديد، لا مفتاح إصدارك؛ Android لا يقبل تحديثًا بشهادة مختلفة. استخدم مفتاحك الأصلي لبناء إصدار تحديث معتمد. لا توجد مفاتيح أو mapping خاصة أو بيانات تفعيل أو كاش ضمن المصدر المسلّم.

اختبار الأرشيف والموديول اختياري وقراءة فقط:

```bash
python3 scripts/inspect-lspatch-compatibility.py /path/to/lspatch.zip /path/to/new-module.apk
```

نتائج الاختبارات وLint وبصمة APK محفوظة في `UI_AUDIT_VALIDATION_AR.md`. الاختبارات لا تشغّل APK مضيفًا أو خادم التفعيل؛ تشغيل GPS يحتاج ترخيصًا صالحًا وruntime فعليًا واستجابة المحرك، مع بقاء Mock قابلًا للكشف. لا يُضمن خلو كل هاتف من الأخطاء أو زمن إطار محدد دون تجربة الجهاز.
