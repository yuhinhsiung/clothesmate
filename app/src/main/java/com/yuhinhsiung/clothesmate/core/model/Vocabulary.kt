package com.yuhinhsiung.clothesmate.core.model

/**
 * 全站枚举与词表 —— 开发文档 §3.4「实现前以此为准」。
 *
 * DB 存 key 而非中文：类别存小类 key（未细分的存大类 key），颜色存 12 色 key，
 * 季节存 spring/summer/autumn/winter。中文名只在 UI 层取，便于将来多语言。
 */

/**
 * 类别两级词表。
 *
 * DB 存 [Sub] 的 key；未细分的（连衣裙 / 帽子）大类小类同值，直接存大类 key。
 * @property topKey 所属大类 key，用于类别行筛选（衣物选择器第二行 chips）
 */
enum class ClothingCategory(
    val key: String,
    val displayName: String,
    val topKey: String,
    val topDisplayName: String,
) {
    // 上衣
    TShirt("t_shirt", "T恤", "top", "上衣"),
    Shirt("shirt", "衬衫", "top", "上衣"),
    Hoodie("hoodie", "卫衣", "top", "上衣"),
    Sweater("sweater", "毛衣", "top", "上衣"),
    TopOther("top_other", "其他上衣", "top", "上衣"),

    // 外套
    Jacket("jacket", "夹克", "outer", "外套"),
    Coat("coat", "大衣", "outer", "外套"),
    Down("down", "羽绒服", "outer", "外套"),
    OuterOther("outer_other", "其他外套", "outer", "外套"),

    // 下装
    Shorts("shorts", "短裤", "bottom", "下装"),
    Pants("pants", "长裤", "bottom", "下装"),
    SkirtShort("skirt_short", "半身裙", "bottom", "下装"),
    SkirtLong("skirt_long", "长裙", "bottom", "下装"),
    BottomOther("bottom_other", "其他下装", "bottom", "下装"),

    // 连衣裙（不再细分）
    Dress("dress", "连衣裙", "dress", "连衣裙"),

    // 鞋子
    Sneakers("sneakers", "运动鞋", "shoes", "鞋子"),
    Loafers("loafers", "皮鞋·乐福", "shoes", "鞋子"),
    Boots("boots", "靴子", "shoes", "鞋子"),
    Sandals("sandals", "凉鞋", "shoes", "鞋子"),
    ShoesOther("shoes_other", "其他鞋", "shoes", "鞋子"),

    // 包包
    Handbag("handbag", "手提包", "bag", "包包"),
    ShoulderBag("shoulder", "单肩包", "bag", "包包"),
    Backpack("backpack", "双肩包", "bag", "包包"),
    BagOther("bag_other", "其他包", "bag", "包包"),

    // 帽子（不再细分）
    Hat("hat", "帽子", "hat", "帽子"),

    // 配饰
    Scarf("scarf", "围巾", "acc", "配饰"),
    Belt("belt", "腰带", "acc", "配饰"),
    AccOther("acc_other", "其他配饰", "acc", "配饰"),
    ;

    companion object {
        fun fromKey(key: String?): ClothingCategory? = entries.find { it.key == key }

        /** 大类 key → 中文名，用于衣物选择器的类别行 chips */
        val topLevels: List<Pair<String, String>> =
            entries.map { it.topKey to it.topDisplayName }.distinctBy { it.first }

        /** 筛选行合并词 key（帽子并入配饰侧）——须先于 [filterChips] 声明（companion 初始化序） */
        val MERGED_FILTER_KEY = "shoes_bag_acc"
        val SHOES_BAG_ACC_FILTER: Set<String> = setOf("shoes", "bag", "acc", "hat")

        /**
         * 筛选行专用的类别 chips（UI 文档 §7.4：全部 / 上衣 / 外套 / 下装 / 连衣裙 / 鞋包配饰）。
         * 「鞋包配饰」合并 [SHOES_BAG_ACC_FILTER] 四个大类（帽子按语义并入配饰侧）。
         *
         * review B-28/B-27：此前衣物选择器与搜索页各硬编码一份且不一致（搜索页缺合并词），
         * 收敛为这一处唯一词源，两处共用。
         */
        val filterChips: List<Pair<String?, String>> = listOf(
            null to "全部",
            "top" to "上衣",
            "outer" to "外套",
            "bottom" to "下装",
            "dress" to "连衣裙",
            MERGED_FILTER_KEY to "鞋包配饰",
        )

        /** 大类筛选 key（含合并词）→ 命中的小类 key 集合；null = 不筛选 */
        fun filterCategoryKeys(topKey: String?): Set<String>? = when (topKey) {
            null -> null
            MERGED_FILTER_KEY -> entries.filter { it.topKey in SHOES_BAG_ACC_FILTER }.map { it.key }.toSet()
            else -> entries.filter { it.topKey == topKey }.map { it.key }.toSet()
        }

        /** 「鞋包配饰」合并词命中的三个大类（搜索框提示语用词，review D-n） */
        private val SHOES_BAG_ACC = setOf("shoes", "bag", "acc")

        /**
         * 搜索别名表：用户输 中文小类名 / 中文大类名 / 英文 key → 命中的小类集合。
         *
         * 例：「衬衫」→ {shirt}；「上衣」→ {t_shirt, shirt, hoodie, sweater, top_other}。
         * DB 只存小类 key，故大类词要展开成该大类下所有小类（W-07 类别命中）。
         */
        val searchAliases: Map<String, List<ClothingCategory>> = buildMap {
            // 注意：buildMap 作用域内的裸 entries 会解析成 Map.entries，须显式限定枚举
            ClothingCategory.entries.forEach { category ->
                put(category.displayName, listOf(category))
                put(category.key, listOf(category))
            }
            // 大类名 → 该大类全部小类（不覆盖已存在的小类别名）
            ClothingCategory.entries
                .groupBy { it.topKey }
                .forEach { (_, categories) ->
                    val topName = categories.first().topDisplayName
                    putIfAbsent(topName, categories)
                }
            // 口语补全：搜索框提示语写「上衣/外套/鞋包配饰」，用户可能少打一个字
            putIfAbsent("上衣", ClothingCategory.entries.filter { it.topKey == "top" })
            putIfAbsent("裤", listOfNotNull(
                fromKey("pants"), fromKey("shorts"),
            ))
            // review D-n：提示语里的合并词「鞋包配饰」要能命中三个大类
            putIfAbsent(
                "鞋包配饰",
                ClothingCategory.entries.filter { it.topKey in SHOES_BAG_ACC },
            )        }
    }
}

/**
 * 12 色枚举（开发文档 §3.4）。DB 存 key，UI 取 [displayName]，色点取 [ClothingColors]。
 *
 * @property rgb 该色的 sRGB 值（0xRRGGBB，**不含 alpha**）。这是全工程唯一的色值来源：
 *   UI 侧 `ui/theme/Color.kt` 由它构造 Compose Color，M2-02 本地颜色提取的 Lab 锚点
 *   也由它换算，避免「色板」与「提取映射表」各写一份色值。
 */
enum class ClothingColor(val key: String, val displayName: String, val rgb: Int) {
    Black("black", "黑", 0x1F1A1D),
    White("white", "白", 0xFFFFFF),
    Gray("gray", "灰", 0x9CA3AF),
    Red("red", "红", 0xDC2626),
    Orange("orange", "橙", 0xEA580C),
    Yellow("yellow", "黄", 0xFACC15),
    Green("green", "绿", 0x16A34A),
    Blue("blue", "蓝", 0x2563EB),
    Purple("purple", "紫", 0x9333EA),
    Brown("brown", "棕", 0x92400E),
    Pink("pink", "粉", 0xEC4899),
    /** 花色不参与最近色映射；rgb 仅用于单色点近似（UI 文档 §6.5 说明） */
    Multi("multi", "花色", 0xDB2777),
    ;

    companion object {
        fun fromKey(key: String?): ClothingColor? = entries.find { it.key == key }

        /**
         * 本地颜色提取的 Lab 最近色映射目标：`multi` 由「≥2 个主色各占 ≥20%」单独判定，
         * 不参与最近色计算，故排除。
         */
        val paletteForExtraction: List<ClothingColor> = entries.filter { it != Multi }

        /**
         * 搜索别名表（W-07）。
         *
         * 除 key 与中文 displayName（「red」/「红」）外，还收口语词「红色」——
         * UI 文档 §7.2 的搜索入口提示语是「搜编号 / 颜色 / 类别」，用户会照口语输入。
         * 单字颜色统一补一条「X 色」形式，不逐条手写。
         */
        val searchAliases: Map<String, ClothingColor> = buildMap {
            // 注意：buildMap 作用域内的裸 entries 会解析成 Map.entries，须显式限定枚举
            ClothingColor.entries.forEach { color ->
                put(color.key, color)
                put(color.displayName, color)
                // 口语形式：「红」→「红色」；花色不造词
                if (color != Multi && color.displayName.length == 1) {
                    put("${color.displayName}色", color)
                }
            }
        }
    }
}

/** 季节词表（全站统一，衣物与穿搭一致，开发文档 §3.4） */
enum class Season(val key: String, val displayName: String) {
    Spring("spring", "春"),
    Summer("summer", "夏"),
    Autumn("autumn", "秋"),
    Winter("winter", "冬"),
    ;

    companion object {
        fun fromKey(key: String?): Season? = entries.find { it.key == key }

        /** 搜索别名表：中文季节名与英文 key 都能命中（W-07 / M3-02 行程标签同源） */
        val searchAliases: Map<String, Season> = buildMap {
            // 注意：buildMap 作用域内的裸 entries 会解析成 Map.entries，须显式限定枚举
            Season.entries.forEach { season ->
                put(season.displayName, season)
                put(season.key, season)
            }
            // review D-n：双字口语——用户会输「春季 / 夏天 / 秋天 / 冬天」，
            // 词表只有单字「春/夏/秋/冬」，不补就搜不到
            put("春季", Spring)
            put("夏天", Summer)
            put("秋天", Autumn)
            put("冬天", Winter)
        }
    }
}

/** 标签类型（tag_dictionary.type） */
enum class TagType(val key: String) {
    Style("style"),
    Scene("scene"),
    Season("season"),
    ;

    companion object {
        fun fromKey(key: String?): TagType? = entries.find { it.key == key }
    }
}

/** 衣物状态（开发文档 §3.2，仅两种） */
object ClothingStatus {
    const val NORMAL = "normal"
    const val STORED = "stored"
}
