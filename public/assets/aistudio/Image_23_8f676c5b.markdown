---
name: Al-Makhzanji Lexicon
colors:
  surface: '#131315'
  surface-dim: '#131315'
  surface-bright: '#39393b'
  surface-container-lowest: '#0e0e10'
  surface-container-low: '#1b1b1d'
  surface-container: '#201f21'
  surface-container-high: '#2a2a2c'
  surface-container-highest: '#353437'
  on-surface: '#e5e1e4'
  on-surface-variant: '#e2beba'
  inverse-surface: '#e5e1e4'
  inverse-on-surface: '#313032'
  outline: '#a98985'
  outline-variant: '#5a413e'
  surface-tint: '#ffb4ab'
  primary: '#ffb4ab'
  on-primary: '#690004'
  primary-container: '#f85c4f'
  on-primary-container: '#5c0003'
  inverse-primary: '#b22923'
  secondary: '#a8c8ff'
  on-secondary: '#003061'
  secondary-container: '#005bb0'
  on-secondary-container: '#bfd6ff'
  tertiary: '#f6bd56'
  on-tertiary: '#422c00'
  tertiary-container: '#ba8825'
  on-tertiary-container: '#3a2600'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#ffdad5'
  primary-fixed-dim: '#ffb4ab'
  on-primary-fixed: '#410002'
  on-primary-fixed-variant: '#900d0f'
  secondary-fixed: '#d5e3ff'
  secondary-fixed-dim: '#a8c8ff'
  on-secondary-fixed: '#001b3c'
  on-secondary-fixed-variant: '#00468a'
  tertiary-fixed: '#ffdeab'
  tertiary-fixed-dim: '#f6bd56'
  on-tertiary-fixed: '#271900'
  on-tertiary-fixed-variant: '#5f4100'
  background: '#131315'
  on-background: '#e5e1e4'
  surface-variant: '#353437'
typography:
  display-lg:
    fontFamily: Cairo
    fontSize: 30px
    fontWeight: '700'
    lineHeight: 40px
  headline-lg:
    fontFamily: Cairo
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 34px
  headline-md:
    fontFamily: Cairo
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 28px
  headline-sm:
    fontFamily: Cairo
    fontSize: 15px
    fontWeight: '600'
    lineHeight: 24px
  body-lg:
    fontFamily: Cairo
    fontSize: 15px
    fontWeight: '400'
    lineHeight: 26px
  body-md:
    fontFamily: Cairo
    fontSize: 13px
    fontWeight: '400'
    lineHeight: 22px
  body-sm:
    fontFamily: Cairo
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 20px
  label-lg:
    fontFamily: Cairo
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
  label-md:
    fontFamily: Cairo
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 18px
  label-sm:
    fontFamily: Cairo
    fontSize: 10px
    fontWeight: '700'
    lineHeight: 16px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  margin: 1rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 0.75rem
  space-lg: 1rem
  space-xl: 1.5rem
---

## Brand & Style

هذا النظام مصمم لتطبيق معجم ومترجم احترافي ثنائي اللغة (عربي-إنجليزي) موجه لنظام أندرويد. يرتكز الأسلوب البصري على مزيج متقن بين مبادئ **Material 3 الحديثة** و**الجمالية الوظيفية الداكنة المركزة (Dark Utilitarian Minimalism)** لتوفير بيئة قراءة ومعاينة معجمية مريحة خالية من التشتيت البصري مع وضوح فائق للنصوص.

### السمات الجوهرية للعلامة:
- **الدقة والموثوقية اللغوية:** واجهة صارمة وواضحة تقدم المعاني، السياقات، والاشتقاقات دون بهرجة غير مبررة.
- **التوجيه البصري الفوري (Fast Visual Wayfinding):** تمييز بصري عالي الدقة بين أجزاء الكلام، النطق الصوتي، ومستويات الاستخدام (رسمي، دارج، عامي).
- **سهولة الوصول والاستخدام باليد الواحدة:** مراعاة مناطق الإبهام ومناطق اللمس المتوافقة مع معايير شاشات الهواتف الحديثة، مع توجيه لغوي كامل من اليمين إلى اليسار (RTL).

## Colors

يعتمد النظام على لوحة ألوان داكنة عالية التباين وذات دور دلالي محدد لكل نغمة لونية لمنع التشتت أثناء البحث اللغوي والترجمة:

- **اللون الأساسي (#F0564A):** أحمر دافئ نشط؛ يُستعمل كمؤشر للحالات النشطة (Active Tabs)، أيقونات الاستماع الصوتي (Audio Pronunciation)، وسياقات التأكيد اللغوي والمفردات المعلمة بنجمة.
- **اللون الثانوي (#6AA6FF):** أزرق كهربائي ساطع؛ مخصص للأدوات التفاعلية، الروابط التشعبية، الحالات المركّزة لحقول البحث (Focus Borders)، والمفردات ذات الصلة (Cross-references).
- **اللون الثالث (#E8B04B):** ذهبي عنبري خافت؛ يُكرس خصيصاً للشرائح التصنيفية للغة (Register Badges: فصيح، معاصر، عامي، تقني، أدبي).
- **الخلفية والأسطح:**
  - خلفية Canvas الأساسية: `#0E0E10` لتحقيق أقصى درجات توفير الطاقة في شاشات OLED وراحة العين.
  - الحاويات والبطاقات (Surface Containers): `#1E2024` لرفع المحتوى مع الحفاظ على العمق الداكن.
  - الحدود والخطوط الفاصلة (Borders & Dividers): `#2A2D33` لتقسيم الكتل دون توليد تباين حاد ومزعج.
- **التيبوغرافيا والنصوص:**
  - النص الأساسي: `#ECECEC` (تباين عالي لسهولة القراءة السريعة).
  - النص الثانوي والبيانات الوصفية: `#A7ABB2` (نبرة هادئة للنطق الصوتي والتعريفات الثانوية).

## Typography

يستخدم النظام عائلة الخط العربي المعاصر **Cairo** لجميع المستويات النصية نظراً لهندسته الواضحة في الحروف اللاتينية والعربية وتوازنه الممتاز على شاشات الهاتف المحمول بكثافات مختلفة (DPI).

### قواعد الاستخدام:
- **عنوان المدخل المعجمي (`display-lg`):** مخصص لاسم الكلمة الأساسية المبحوث عنها (Headword) بحجم 30px ووزن عريض 700 ليعطي حضوراً بصرياً فورياً ومستقلاً.
- **عناوين الأقسام (`headline-sm`):** بحجم 15px ووزن 600 لعناوين التبويبات والمعاني الفرعية والتراكيب الاصطلاحية (Idioms).
- **المتن العام والشروحات (`body-md`):** بحجم 13px محكم مع ارتفاع سطر 22px لتسهيل قراءة التعريفات الطويلة والأمثلة المقتبسة دون إرهاق العين.
- **التصنيفات والوسوم (`label-sm` و `label-md`):** أوزان مشدودة (600 و 700) بحجم 10px-12px لكتابة صيغ الكلمة (Grammatical POS)، الصواتيات الفونيتية (IPA)، والتصنيفات الأسلوبية.

## Layout & Spacing

يعتمد نظام التخطيط على شبكة هواتف مرنة تعتمد مقياس 4dp/8dp مع التركيز الكامل على بيئات الاستخدام من اليمين إلى اليسار (RTL First):

- **الهوامش الجانبية (Canvas Margins):** قيمة ثابتة بمقدار `1rem` (16dp) تضمن بقاء النصوص بعيدة عن حواف الشاشات المنحنية وشريط تنقل النظام بأندرويد.
- **مسافات الفصل بين البطاقات (Gutters & Stack Gaps):** تستخدم مسافات موحدة `0.75rem` (12dp) إلى `1rem` (16dp) للحفاظ على اتصال المعاني في سياق البطاقة الواحدة وفصل المدخلات المستقلة بوضوح.
- **أهداف اللمس المريحة (Touch Targets):** جميع الأزرار، التبديلات، وأيقونات الصوت لا تقل أبعادها القابلة للنقر عن `48dp × 48dp` (أو مساحة تفاعلية لا تقل عن `44dp` مع Padding تكميلي) لتحقيق سهولة تامة أثناء الاستخدام بيد واحدة على متن المواصلات أو أثناء الحركة السريعة.

## Elevation & Depth

يعتمد التصميم نهج **الطبقات اللونية المحايدة (Tonal Layering)** المتوافق مع شاشات OLED بدلاً من الظلال التقليدية الكثيفة التي تسبب تشويشاً على الخلفيات شديدة العتمة:

- **المستوى 0 (Base Canvas):** اللون `#0E0E10`، يمثل قاع الواجهة لشريط الحالة وخلفية المحتوى الممتد.
- **المستوى 1 (Cards & Containers):** اللون `#1E2024`، يُرفع المحتوى المعجمي وحقول الإدخال عبر هذا السطح مع تحديد ناعم جداً بواسطة حدود دقيقة بلون `#2A2D33` وبسمك `1dp`.
- **المستوى 2 (Floating Search & Sheets):** بطاقة البحث العائمة وشرائح التصفح السفلية (Bottom Sheets) تستخدم نفس السطح `#1E2024` مع إضافة ظل محيطي فائق النعومة وخافت جداً بلون داكن بنسبة عتامة منخفضة لتأكيد الانفصال الهرمي: `box-shadow: 0 8px 24px rgba(0, 0, 0, 0.45)`.
- **حالات التركيز (Active Focus):** لا تُستخدم الظلال للتعبير عن النشاط، بل يستعاض عنها بإطار توهج دقيق (1.5dp) بلون `#6AA6FF` على حاوية الإدخال النشطة.

## Shapes

تتبنى الواجهة استراتيجية الانحناء المتوازن (Rounded Level 2):
- **البطاقات وحاويات القاموس:** تستخدم انحناءاً ناعماً بمقدار `1rem` (16dp) لإبراز كتل المعاني المستقلة وإعطاء إحساس لطيف ومريح للعين.
- **حقل شريط البحث:** زوايا مستديرة بمقدار `0.75rem` (12dp) تجمع بين الثبات المؤسسي والديناميكية الحديثة.
- **الشرائح التصنيفية (Badges & Chips):** انحناءات نصف دائرية مريحة لتوليد كبسولات مميزة ومتباينة بصرياً عن شكل البطاقات المحيطة بها.

## Components

### 1. شريط البحث المعجمي (Search Bar)
- خلفية `#1E2024`، مع حدود افتراضية `#2A2D33` تتحول بسلاسة عند التحديد إلى إطار بارز باللون الأزرق الساطع `#6AA6FF`.
- يحتوي على أيقونة تبديل لغة البحث (عربي / إنجليزي)، زر تفريغ الحقل، ومفتاح الإدخال الصوتي، بحد أدنى لمساحة اللمس `48dp`.

### 2. بطاقة رأس الكلمة (Headword Lexicon Card)
- بطاقة بلون `#1E2024` مع حدود `#2A2D33`.
- الكلمة المبحوث عنها معروضة بخط `Cairo` عريض بحجم 30px ولون `#ECECEC`.
- زر النطق الصوتي (Audio Pronunciation Icon Button) بحجم لمس `44dp`، لون الأيقونة `#F0564A` مع تأثير ضغط حركي يمنح إشارة إرجاع مرئية سريعة.
- شريط النطق الفونيتي (IPA) يُعرض بلون النص الثانوي الخافت `#A7ABB2`.

### 3. الشرائح التصنيفية (Lexical Chips & Badges)
- شرائح أسلوب الاستخدام (مثل: "رسمي"، "دارج"، "اصطلاح"): خلفية خافتة منبثقة من الذهبي مع نص ناصع بلون `#E8B04B` وحدود خارجية ناعمة بلون `#E8B04B` بدرجة عتامة 30%.
- شرائح أقسام الكلام (اسم، فعل، صفة): خلفية رمادية هادئة من نسيج `#2A2D33` مع نص بلون `#ECECEC`.

### 4. التبويبات النشطة (Navigation & Context Tabs)
- مؤشر التبويب النشط يستخدم خطاً سفلياً دقيقاً أو كبسولة تعبئة باللون الأحمر الدافئ `#F0564A` مع نص بلون `#ECECEC`.
- التبويبات غير النشطة تعرض نصوصها بلون `#A7ABB2`.

### 5. حقول الترجمة المزدوجة (Dual Translation Card)
- بطاقة مقسمة إلى قسمين مع خط فاصل أفقي بلون `#2A2D33`.
- نصف للإدخال والآخر للنتيجة الفورية، مع أزرار نسخ، مشاركة، واستماع مثبتة في زوايا الحاوية وموزعة وفق تدفق اليمين لليسار (RTL).