package com.rjy.xyz.apps.xyzinfo.data.soc

import java.util.Locale

/**
 * SoC 规格库。
 *
 * 规格数据量较大，单独放在本文件；匹配算法集中在 [findBestSpec]。
 */
object SocSpecRepository {

    private val specs = listOf(
        // =========================
        // Snapdragon
        // =========================
        SocSpec(
            displayName = "骁龙 8 Elite",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "顶级旗舰",
            aliases = listOf("SM8750", "SM8750AB", "SM8750AC", "SNAPDRAGON8ELITE", "SNAPDRAGON8GEN4", "骁龙8至尊版"),
            cpuClusters = "2+6 自研 Oryon",
            cpuArchitecture = "ARM64 / Oryon",
            gpuName = "Adreno 830",
            gpuCores = "未公开",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "骁龙 8 Gen 3",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "顶级旗舰",
            aliases = listOf("SM8650", "SM8650AB", "SM8650AC", "SNAPDRAGON8GEN3", "骁龙8GEN3"),
            cpuClusters = "1×3.30 + 5×3.20 + 2×2.30 GHz",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Adreno 750",
            gpuCores = "未公开",
            graphicsApi = "OpenGL ES 3.2 / Vulkan 1.3"
        ),
        SocSpec(
            displayName = "骁龙 8s Gen 3",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "次旗舰",
            aliases = listOf("SM8635", "SNAPDRAGON8SGEN3"),
            cpuClusters = "1+4+3",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Adreno 735",
            gpuCores = "未公开",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "骁龙 8 Gen 2",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "顶级旗舰",
            aliases = listOf("SM8550", "SM8550AB", "SM8550AC", "SNAPDRAGON8GEN2"),
            cpuClusters = "1+4+3",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Adreno 740",
            gpuCores = "未公开",
            graphicsApi = "OpenGL ES 3.2 / Vulkan 1.3"
        ),
        SocSpec(
            displayName = "骁龙 8+ Gen 1",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "旗舰",
            aliases = listOf("SM8475", "SNAPDRAGON8PLUSGEN1"),
            cpuClusters = "1+3+4",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Adreno 730",
            gpuCores = "未公开",
            graphicsApi = "OpenGL ES 3.2 / Vulkan 1.1"
        ),
        SocSpec(
            displayName = "骁龙 8 Gen 1",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "旗舰",
            aliases = listOf("SM8450", "SNAPDRAGON8GEN1"),
            cpuClusters = "1+3+4",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Adreno 730",
            gpuCores = "未公开",
            graphicsApi = "OpenGL ES 3.2 / Vulkan 1.1"
        ),
        SocSpec(
            displayName = "骁龙 888",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "旗舰",
            aliases = listOf("SM8350", "SNAPDRAGON888"),
            cpuClusters = "1+3+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 660",
            gpuCores = "未公开",
            graphicsApi = "OpenGL ES 3.2 / Vulkan 1.1"
        ),
        SocSpec(
            displayName = "骁龙 870",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "旗舰",
            aliases = listOf("SM8250AC", "SM8250-AC", "SNAPDRAGON870"),
            cpuClusters = "1×3.20 + 3×2.42 + 4×1.80 GHz",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 650",
            gpuCores = "未公开",
            gpuMaxFreqMHz = 670,
            graphicsApi = "OpenGL ES 3.2 / Vulkan 1.1"
        ),
        SocSpec(
            displayName = "骁龙 865+",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "旗舰",
            aliases = listOf("SM8250AB", "SM8250-AB", "SNAPDRAGON865PLUS"),
            cpuClusters = "1×3.10 + 3×2.42 + 4×1.80 GHz",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 650",
            gpuCores = "未公开",
            gpuMaxFreqMHz = 670,
            graphicsApi = "OpenGL ES 3.2 / Vulkan 1.1"
        ),
        SocSpec(
            displayName = "骁龙 865",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "旗舰",
            aliases = listOf("SM8250", "SNAPDRAGON865"),
            cpuClusters = "1×2.84 + 3×2.42 + 4×1.80 GHz",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 650",
            gpuCores = "未公开",
            gpuMaxFreqMHz = 587,
            graphicsApi = "OpenGL ES 3.2 / Vulkan 1.1"
        ),
        SocSpec(
            displayName = "骁龙 860",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "旗舰",
            aliases = listOf("SM8150AC", "SM8150-AC", "SNAPDRAGON860"),
            deviceKeywords = listOf("POCO X3 PRO", "VAYU", "BHIMA", "XIAOMI PAD 5", "NABU"),
            cpuClusters = "1×2.96 + 3×2.42 + 4×1.80 GHz",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 640",
            gpuCores = "384 ALUs",
            gpuMaxFreqMHz = 675,
            graphicsApi = "OpenGL ES 3.2 / Vulkan 1.1 / OpenCL 2.0"
        ),
        SocSpec(
            displayName = "骁龙 855+",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "旗舰",
            aliases = listOf("SM8150P", "SM8150-P", "SNAPDRAGON855PLUS"),
            cpuClusters = "1×2.96 + 3×2.42 + 4×1.80 GHz",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 640",
            gpuCores = "384 ALUs",
            gpuMaxFreqMHz = 672,
            graphicsApi = "OpenGL ES 3.2 / Vulkan 1.1 / OpenCL 2.0"
        ),
        SocSpec(
            displayName = "骁龙 855",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "旗舰",
            aliases = listOf("SM8150", "SDM855", "SNAPDRAGON855"),
            cpuClusters = "1×2.84 + 3×2.42 + 4×1.80 GHz",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 640",
            gpuCores = "384 ALUs",
            gpuMaxFreqMHz = 585,
            graphicsApi = "OpenGL ES 3.2 / Vulkan 1.1 / OpenCL 2.0"
        ),
        SocSpec(
            displayName = "骁龙 845",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "次旗舰",
            aliases = listOf("SDM845", "SNAPDRAGON845"),
            cpuClusters = "4+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 630",
            gpuCores = "256 ALUs",
            graphicsApi = "OpenGL ES 3.2 / Vulkan 1.0"
        ),

        // =========================
        // MediaTek
        // =========================
        SocSpec(
            displayName = "天玑 9400",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "顶级旗舰",
            aliases = listOf("MT6991", "DIMENSITY9400", "DIMENSITY9400PLUS"),
            cpuClusters = "全大核架构",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Immortalis-G925",
            gpuCores = "12 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "天玑 9300",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "顶级旗舰",
            aliases = listOf("MT6989", "DIMENSITY9300", "DIMENSITY9300PLUS"),
            cpuClusters = "全大核架构",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Immortalis-G720",
            gpuCores = "12 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "天玑 9200+",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "旗舰",
            aliases = listOf("MT6985", "DIMENSITY9200PLUS"),
            cpuClusters = "1+3+4",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Immortalis-G715",
            gpuCores = "11 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "天玑 9000 / 9000+",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "旗舰",
            aliases = listOf("MT6983", "DIMENSITY9000"),
            cpuClusters = "1+3+4",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Mali-G710",
            gpuCores = "10 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "天玑 8300",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "中高端",
            aliases = listOf("MT6897", "DIMENSITY8300", "DIMENSITY8350"),
            cpuClusters = "4+4",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Mali-G615",
            gpuCores = "6 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "天玑 8200",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "中高端",
            aliases = listOf("MT6895", "DIMENSITY8200"),
            cpuClusters = "1+3+4",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Mali-G610",
            gpuCores = "6 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "天玑 7200 / 7300",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "中端",
            aliases = listOf("MT6886", "DIMENSITY7200", "DIMENSITY7300"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Mali-G610",
            gpuCores = "4 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "天玑 7050 / 6100+",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "中端",
            aliases = listOf("DIMENSITY7050", "DIMENSITY6100"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G68",
            gpuCores = "4 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "天玑 930 / 1080",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "中端",
            aliases = listOf("MT6855", "DIMENSITY930", "DIMENSITY1080"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G68",
            gpuCores = "4 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "天玑 700 / 810",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "中端",
            aliases = listOf("MT6833", "MT6833P", "DIMENSITY700", "DIMENSITY810"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G57",
            gpuCores = "2 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "Helio G99",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "中端",
            aliases = listOf("MT6789", "HELIOG99"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G57",
            gpuCores = "2 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "Helio G95 / G90T",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "中端",
            aliases = listOf("MT6785", "MT6785T", "HELIOG95", "HELIOG90T"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G76",
            gpuCores = "4 核",
            graphicsApi = "OpenGL ES 3.2 / Vulkan 1.1"
        ),

        // =========================
        // Exynos
        // =========================
        SocSpec(
            displayName = "Exynos 2500",
            brandName = "三星 Exynos",
            badgeText = "猎户座",
            performanceLevel = "顶级旗舰",
            aliases = listOf("S5E9955", "EXYNOS2500", "UNIVERSAL2500"),
            cpuClusters = "1+2+5",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Xclipse",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "Exynos 2400",
            brandName = "三星 Exynos",
            badgeText = "猎户座",
            performanceLevel = "旗舰",
            aliases = listOf("S5E9945", "EXYNOS2400", "UNIVERSAL2400"),
            cpuClusters = "1+2+3+4",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Xclipse 940",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "Exynos 2200",
            brandName = "三星 Exynos",
            badgeText = "猎户座",
            performanceLevel = "中高端",
            aliases = listOf("S5E9935", "EXYNOS2200", "UNIVERSAL2200"),
            cpuClusters = "1+3+4",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Xclipse 920",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "Exynos 2100",
            brandName = "三星 Exynos",
            badgeText = "猎户座",
            performanceLevel = "中高端",
            aliases = listOf("S5E9925", "EXYNOS2100", "UNIVERSAL2100"),
            cpuClusters = "1+3+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G78",
            gpuCores = "14 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),

        // =========================
        // Kirin
        // =========================
        SocSpec(
            displayName = "麒麟 9020",
            brandName = "华为麒麟",
            badgeText = "麒麟",
            performanceLevel = "顶级旗舰",
            aliases = listOf("KIRIN9020"),
            cpuClusters = "未知",
            cpuArchitecture = "ARM64",
            gpuName = "Maleoon",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "麒麟 9010",
            brandName = "华为麒麟",
            badgeText = "麒麟",
            performanceLevel = "旗舰",
            aliases = listOf("KIRIN9010"),
            cpuClusters = "未知",
            cpuArchitecture = "ARM64",
            gpuName = "Maleoon",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "麒麟 9000S",
            brandName = "华为麒麟",
            badgeText = "麒麟",
            performanceLevel = "旗舰",
            aliases = listOf("KIRIN9000S", "KIRIN9000SL"),
            cpuClusters = "1+3+4",
            cpuArchitecture = "ARM64",
            gpuName = "Maleoon 910",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "麒麟 9000 / 9000E",
            brandName = "华为麒麟",
            badgeText = "麒麟",
            performanceLevel = "旗舰",
            aliases = listOf("KIRIN9000", "KIRIN9000E", "HI3690"),
            cpuClusters = "1+3+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G78",
            gpuCores = "24 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "麒麟 990",
            brandName = "华为麒麟",
            badgeText = "麒麟",
            performanceLevel = "中高端",
            aliases = listOf("KIRIN990", "KIRIN9905G"),
            cpuClusters = "2+2+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G76",
            gpuCores = "16 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "麒麟 980",
            brandName = "华为麒麟",
            badgeText = "麒麟",
            performanceLevel = "中高端",
            aliases = listOf("KIRIN980", "HI3680"),
            cpuClusters = "2+2+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G76",
            gpuCores = "10 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "麒麟 970",
            brandName = "华为麒麟",
            badgeText = "麒麟",
            performanceLevel = "次旗舰",
            aliases = listOf("KIRIN970", "HI3670"),
            cpuClusters = "4+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G72",
            gpuCores = "12 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),

        // =========================
        // UNISOC
        // =========================
        SocSpec(
            displayName = "紫光展锐 T820",
            brandName = "紫光展锐 / 展讯",
            badgeText = "展锐",
            performanceLevel = "中端",
            aliases = listOf("UMS9620", "T820"),
            cpuClusters = "1+3+4",
            cpuArchitecture = "ARM64",
            gpuName = "Mali-G57",
            gpuCores = "4 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "紫光展锐 T770",
            brandName = "紫光展锐 / 展讯",
            badgeText = "展锐",
            performanceLevel = "中端",
            aliases = listOf("T770"),
            cpuClusters = "1+3+4",
            cpuArchitecture = "ARM64",
            gpuName = "Mali-G57",
            gpuCores = "4 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "紫光展锐 T760",
            brandName = "紫光展锐 / 展讯",
            badgeText = "展锐",
            performanceLevel = "中端",
            aliases = listOf("T760"),
            cpuClusters = "1+3+4",
            cpuArchitecture = "ARM64",
            gpuName = "Mali-G57",
            gpuCores = "4 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "紫光展锐 T618",
            brandName = "紫光展锐 / 展讯",
            badgeText = "展锐",
            performanceLevel = "中端",
            aliases = listOf("T618"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARM64",
            gpuName = "Mali-G52",
            gpuCores = "2 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "紫光展锐 T616",
            brandName = "紫光展锐 / 展讯",
            badgeText = "展锐",
            performanceLevel = "中端",
            aliases = listOf("T616"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARM64",
            gpuName = "Mali-G57",
            gpuCores = "1-2 核级别",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "紫光展锐 T610",
            brandName = "紫光展锐 / 展讯",
            badgeText = "展锐",
            performanceLevel = "中端",
            aliases = listOf("T610"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARM64",
            gpuName = "Mali-G52",
            gpuCores = "2 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "紫光展锐 SC9863A",
            brandName = "紫光展锐 / 展讯",
            badgeText = "展锐",
            performanceLevel = "入门",
            aliases = listOf("SC9863A"),
            cpuClusters = "8 核同频",
            cpuArchitecture = "ARM64",
            gpuName = "PowerVR GE8322",
            graphicsApi = "OpenGL ES / Vulkan"
        ),

        // =========================
        // 骁龙补齐（2025-2026 新旗舰 + 中低端缺口）
        // =========================
        SocSpec(
            displayName = "骁龙 8 Elite Gen 5",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "顶级旗舰",
            aliases = listOf("SM8850", "SNAPDRAGON8ELITEGEN5", "骁龙8至尊版GEN5"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Adreno 840",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "骁龙 8s Gen 4",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "旗舰",
            aliases = listOf("SM8735", "SNAPDRAGON8SGEN4"),
            cpuClusters = "1+3+4",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Adreno 825",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "骁龙 778G / 778G+",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "中端",
            aliases = listOf("SM7325", "SM7350", "SNAPDRAGON778G", "SNAPDRAGON778GPLUS"),
            cpuClusters = "1+3+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 642L",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "骁龙 780G",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "中端",
            aliases = listOf("SNAPDRAGON780G"),
            cpuClusters = "1+3+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 642",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "骁龙 7+ Gen 2",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "中高端",
            aliases = listOf("SM7475", "SNAPDRAGON7PLUSGEN2"),
            cpuClusters = "1+3+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 725",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "骁龙 7 Gen 1",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "中端",
            aliases = listOf("SM7450", "SNAPDRAGON7GEN1"),
            cpuClusters = "1+3+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 644",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "骁龙 7+ Gen 3",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "中高端",
            aliases = listOf("SM7675", "SNAPDRAGON7PLUSGEN3"),
            cpuClusters = "1+4+3",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Adreno 732",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "骁龙 7 Gen 3",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "中端",
            aliases = listOf("SM7550", "SNAPDRAGON7GEN3"),
            cpuClusters = "1+3+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 720",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "骁龙 7s Gen 3",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "中端",
            aliases = listOf("SM7635", "SNAPDRAGON7SGEN3"),
            cpuClusters = "1+3+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 810",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "骁龙 7s Gen 2",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "中端",
            aliases = listOf("SM7435", "SNAPDRAGON7SGEN2"),
            cpuClusters = "4+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 710",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "骁龙 6 Gen 3",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "中端",
            aliases = listOf("SM6475", "SNAPDRAGON6GEN3"),
            cpuClusters = "4+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 710",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "骁龙 6 Gen 1",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "中端",
            aliases = listOf("SM6450", "SNAPDRAGON6GEN1"),
            cpuClusters = "4+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 710",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "骁龙 4 Gen 2",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "入门",
            aliases = listOf("SM4450", "SNAPDRAGON4GEN2"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 613",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "骁龙 4 Gen 1 / 480",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "入门",
            aliases = listOf("SM4350", "SNAPDRAGON4GEN1", "SNAPDRAGON480"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 619",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "骁龙 695",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "中端",
            aliases = listOf("SM6375", "SNAPDRAGON695"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 619",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "骁龙 690",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "中端",
            aliases = listOf("SM6350", "SNAPDRAGON690"),
            cpuClusters = "8 核同频",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 619L",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "骁龙 680 / 685",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "入门",
            aliases = listOf("SM6225", "SNAPDRAGON680", "SNAPDRAGON685", "SNAPDRAGON6S"),
            cpuClusters = "4+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 610",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "骁龙 675",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "入门",
            aliases = listOf("SM6150", "SNAPDRAGON675"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 612",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "骁龙 662 / 665",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "入门",
            aliases = listOf("SM6115", "SNAPDRAGON662", "SNAPDRAGON665"),
            cpuClusters = "4+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 610",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "骁龙 660",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "入门",
            aliases = listOf("SDM660", "SNAPDRAGON660"),
            cpuClusters = "4+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 512",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "骁龙 636",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "入门",
            aliases = listOf("SDM636", "SNAPDRAGON636"),
            cpuClusters = "4+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 509",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "骁龙 835",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "中高端",
            aliases = listOf("MSM8998", "SNAPDRAGON835"),
            cpuClusters = "4+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 540",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "骁龙 765G / 765",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "中端",
            aliases = listOf("SM7250", "SNAPDRAGON765G", "SNAPDRAGON765"),
            cpuClusters = "1+1+6",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 620",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "骁龙 750G",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "中端",
            aliases = listOf("SM7225", "SNAPDRAGON750G"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 619",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "骁龙 720G",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "中端",
            aliases = listOf("SM7125", "SNAPDRAGON720G"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 618",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "骁龙 730G / 732G",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "中端",
            aliases = listOf("SM7150", "SNAPDRAGON730G", "SNAPDRAGON732G"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 618",
            graphicsApi = "OpenGL ES / Vulkan"
        ),

        // =========================
        // 天玑补齐
        // =========================
        SocSpec(
            displayName = "天玑 9500",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "顶级旗舰",
            aliases = listOf("MT6993", "DIMENSITY9500"),
            cpuClusters = "1+3+4",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Mali-G1-Ultra",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "天玑 8400 / 8400 Ultra",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "中高端",
            aliases = listOf("MT6899", "DIMENSITY8400", "DIMENSITY8400ULTRA"),
            cpuClusters = "1+3+4",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Mali-G720",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "天玑 8100 / 8100 Max",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "中高端",
            aliases = listOf("DIMENSITY8100", "DIMENSITY8100MAX"),
            cpuClusters = "4+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G610",
            gpuCores = "6 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "天玑 1200 / 1100",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "中高端",
            aliases = listOf("MT6893", "DIMENSITY1200", "DIMENSITY1100"),
            cpuClusters = "1+3+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G77",
            gpuCores = "9 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "天玑 1000+ / 1000",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "中端",
            aliases = listOf("MT6889", "DIMENSITY1000", "DIMENSITY1000PLUS"),
            cpuClusters = "4+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G77",
            gpuCores = "9 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "天玑 800 / 800U",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "中端",
            aliases = listOf("MT6873", "DIMENSITY800", "DIMENSITY800U"),
            cpuClusters = "4+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G57",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "Helio G88 / G85 / G80",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "入门",
            aliases = listOf("MT6769", "MT6768", "HELIOG88", "HELIOG85", "HELIOG80"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G52",
            graphicsApi = "OpenGL ES / Vulkan"
        ),

        // =========================
        // 麒麟补齐
        // =========================
        SocSpec(
            displayName = "麒麟 9030",
            brandName = "华为麒麟",
            badgeText = "麒麟",
            performanceLevel = "顶级旗舰",
            aliases = listOf("KIRIN9030", "HI3630"),
            cpuClusters = "未知",
            cpuArchitecture = "ARM64",
            gpuName = "Maleoon",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "麒麟 9020A",
            brandName = "华为麒麟",
            badgeText = "麒麟",
            performanceLevel = "顶级旗舰",
            aliases = listOf("KIRIN9020A"),
            cpuClusters = "未知",
            cpuArchitecture = "ARM64",
            gpuName = "Maleoon",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "麒麟 985",
            brandName = "华为麒麟",
            badgeText = "麒麟",
            performanceLevel = "中高端",
            aliases = listOf("KIRIN985", "HI6290"),
            cpuClusters = "2+2+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G77",
            gpuCores = "8 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "麒麟 820",
            brandName = "华为麒麟",
            badgeText = "麒麟",
            performanceLevel = "中端",
            aliases = listOf("KIRIN820", "HI6280"),
            cpuClusters = "1+3+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G57",
            gpuCores = "6 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "麒麟 960",
            brandName = "华为麒麟",
            badgeText = "麒麟",
            performanceLevel = "中端",
            aliases = listOf("KIRIN960", "HI3660"),
            cpuClusters = "4+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G71",
            gpuCores = "8 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "麒麟 950 / 955",
            brandName = "华为麒麟",
            badgeText = "麒麟",
            performanceLevel = "入门",
            aliases = listOf("KIRIN950", "KIRIN955", "HI3650"),
            cpuClusters = "4+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-T880",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "麒麟 810 / 710",
            brandName = "华为麒麟",
            badgeText = "麒麟",
            performanceLevel = "入门",
            aliases = listOf("KIRIN810", "KIRIN710", "HI6260"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G52",
            graphicsApi = "OpenGL ES / Vulkan"
        ),

        // =========================
        // 紫光展锐补齐
        // =========================
        SocSpec(
            displayName = "紫光展锐 T730",
            brandName = "紫光展锐 / 展讯",
            badgeText = "展锐",
            performanceLevel = "入门",
            aliases = listOf("UMS9230", "T730"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARM64",
            gpuName = "Mali-G57",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "紫光展锐 T612 / T606",
            brandName = "紫光展锐 / 展讯",
            badgeText = "展锐",
            performanceLevel = "入门",
            aliases = listOf("T612", "T606", "UMS9230E"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARM64",
            gpuName = "Mali-G57",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "紫光展锐 SC9832E",
            brandName = "紫光展锐 / 展讯",
            badgeText = "展锐",
            performanceLevel = "入门",
            aliases = listOf("SC9832E", "SPRD9832E"),
            cpuClusters = "4 核同频",
            cpuArchitecture = "ARM64",
            gpuName = "Mali-T820",
            graphicsApi = "OpenGL ES / Vulkan"
        ),

        // =========================
        // Google Tensor 全系
        // =========================
        SocSpec(
            displayName = "Google Tensor G5",
            brandName = "Google Tensor",
            badgeText = "谷歌",
            performanceLevel = "旗舰",
            aliases = listOf("TENSORG5", "GS501", "GOOGLETENSORG5"),
            cpuClusters = "1+5+2",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "PowerVR DXT-48-1536",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "Google Tensor G4",
            brandName = "Google Tensor",
            badgeText = "谷歌",
            performanceLevel = "旗舰",
            aliases = listOf("TENSORG4", "GS401", "GOOGLETENSORG4"),
            cpuClusters = "1+3+4",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Mali-G715",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "Google Tensor G3",
            brandName = "Google Tensor",
            badgeText = "谷歌",
            performanceLevel = "旗舰",
            aliases = listOf("TENSORG3", "GS301", "ZUMAPRO", "GOOGLETENSORG3"),
            cpuClusters = "1+4+4",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Mali-G715",
            gpuCores = "10 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "Google Tensor G2",
            brandName = "Google Tensor",
            badgeText = "谷歌",
            performanceLevel = "中高端",
            aliases = listOf("TENSORG2", "GS201", "CLOUDRIPPER", "GOOGLETENSORG2"),
            cpuClusters = "2+2+4",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Mali-G710",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "Google Tensor (G1)",
            brandName = "Google Tensor",
            badgeText = "谷歌",
            performanceLevel = "中高端",
            aliases = listOf("TENSORG1", "GS101", "GOOGLETENSORG1", "GOOGLETENSOR"),
            cpuClusters = "2+2+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G78",
            gpuCores = "20 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),

        // =========================
        // 小米 玄戒（XRING）
        // =========================
        SocSpec(
            displayName = "玄戒 O1",
            brandName = "小米玄戒",
            badgeText = "玄戒",
            performanceLevel = "旗舰",
            aliases = listOf("XRINGO1", "O1", "玄戒O1", "XIAOMIO1"),
            cpuClusters = "2+4+2+2",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Immortalis-G925",
            gpuCores = "16 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "玄戒 O3",
            brandName = "小米玄戒",
            badgeText = "玄戒",
            performanceLevel = "旗舰",
            aliases = listOf("XRINGO3", "O3", "玄戒O3", "XIAOMIO3"),
            cpuClusters = "未知",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "未公开",
            graphicsApi = "OpenGL ES / Vulkan"
        ),

        // =========================================================
        // 全量补齐：按厂商列出世代更完整的型号，尽量覆盖老机型
        // 说明：GPU 名称/频率来自公开规格，个别新芯片以“未公开”占位，
        //      后续如果实测不符，直接改这里的对应条目即可。
        // =========================================================

        // ---------- 骁龙：最新世代 ----------
        SocSpec(
            displayName = "骁龙 8 Gen 5",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "顶级旗舰",
            aliases = listOf("SM8845", "SNAPDRAGON8GEN5"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Adreno 830 级别"
        ),
        SocSpec(
            displayName = "骁龙 888+",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "旗舰",
            aliases = listOf("SM8350AC", "SM8350-AC", "SNAPDRAGON888PLUS"),
            cpuClusters = "1+3+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 660"
        ),
        SocSpec(
            displayName = "骁龙 8 Gen 3 for Galaxy",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "顶级旗舰",
            aliases = listOf("SM8650AC", "SNAPDRAGON8GEN3FORGALAXY"),
            cpuClusters = "1+3+2+2",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Adreno 750",
            gpuMaxFreqMHz = 1000
        ),

        // ---------- 骁龙 7 / 6 / 4 系补齐 ----------
        SocSpec(
            displayName = "骁龙 7 Gen 4",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "中高端",
            aliases = listOf("SNAPDRAGON7GEN4"),
            cpuClusters = "1+4+3",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Adreno 722"
        ),
        SocSpec(
            displayName = "骁龙 7s Gen 4",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "中端",
            aliases = listOf("SNAPDRAGON7SGEN4"),
            cpuClusters = "1+3+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 810"
        ),
        SocSpec(
            displayName = "骁龙 6 Gen 4",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "中端",
            aliases = listOf("SNAPDRAGON6GEN4"),
            cpuClusters = "4+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 710"
        ),
        SocSpec(
            displayName = "骁龙 6s Gen 3",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "中端",
            aliases = listOf("SM6375AC", "SM6375-AC", "SNAPDRAGON6SGEN3"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 619"
        ),
        SocSpec(
            displayName = "骁龙 4s Gen 2",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "入门",
            aliases = listOf("SM4635", "SNAPDRAGON4SGEN2"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 613"
        ),
        SocSpec(
            displayName = "骁龙 4 Gen 3",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "入门",
            aliases = listOf("SNAPDRAGON4GEN3"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 613"
        ),

        // ---------- 骁龙：中端老将（7/6 系） ----------
        SocSpec(
            displayName = "骁龙 768G",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "中端",
            aliases = listOf("SM7250AC", "SM7250-AC", "SNAPDRAGON768G"),
            cpuClusters = "1+1+6",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 620"
        ),
        SocSpec(
            displayName = "骁龙 710",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "入门",
            aliases = listOf("SDM710", "SNAPDRAGON710"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 616"
        ),
        SocSpec(
            displayName = "骁龙 670",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "入门",
            aliases = listOf("SDM670", "SNAPDRAGON670"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 615"
        ),
        SocSpec(
            displayName = "骁龙 630",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "入门",
            aliases = listOf("SDM630", "SNAPDRAGON630"),
            cpuClusters = "8 核同频",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 508"
        ),
        SocSpec(
            displayName = "骁龙 625 / 626",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "入门",
            aliases = listOf("MSM8953", "MSM8953PRO", "SNAPDRAGON625", "SNAPDRAGON626"),
            cpuClusters = "8 核同频",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 506"
        ),
        SocSpec(
            displayName = "骁龙 450",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "入门",
            aliases = listOf("SDM450", "SNAPDRAGON450"),
            cpuClusters = "8 核同频",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 506"
        ),
        SocSpec(
            displayName = "骁龙 439 / 429",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "入门",
            aliases = listOf("MSM8940", "MSM8937", "SNAPDRAGON439", "SNAPDRAGON429"),
            cpuClusters = "8 核同频",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 505 / 504"
        ),

        // ---------- 骁龙：旗舰老将（8 系） ----------
        SocSpec(
            displayName = "骁龙 821 / 820",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "中高端",
            aliases = listOf("MSM8996", "MSM8996PRO", "SNAPDRAGON821", "SNAPDRAGON820"),
            cpuClusters = "2+2",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 530"
        ),
        SocSpec(
            displayName = "骁龙 810",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "中端",
            aliases = listOf("MSM8994", "SNAPDRAGON810"),
            cpuClusters = "4+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Adreno 430"
        ),
        SocSpec(
            displayName = "骁龙 801",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "入门",
            aliases = listOf("MSM8974", "MSM8974AC", "SNAPDRAGON801"),
            cpuClusters = "4 核同频",
            cpuArchitecture = "ARMv7",
            gpuName = "Adreno 330"
        ),

        // ---------- 天玑补齐 ----------
        SocSpec(
            displayName = "天玑 920",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "中端",
            aliases = listOf("MT6877", "MT6877V", "MT6877VZA", "DIMENSITY920"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G68",
            gpuCores = "4 核"
        ),
        SocSpec(
            displayName = "天玑 900 / 820",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "中端",
            aliases = listOf("MT6875", "DIMENSITY900", "DIMENSITY820"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G68"
        ),
        SocSpec(
            displayName = "天玑 720",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "中端",
            aliases = listOf("MT6853", "DIMENSITY720"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G57"
        ),
        SocSpec(
            displayName = "天玑 7025 / 8020",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "中端",
            aliases = listOf("MT6878", "DIMENSITY7025", "DIMENSITY8020"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G610"
        ),
        SocSpec(
            displayName = "天玑 6300 / 6080",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "入门",
            aliases = listOf("MT6835", "DIMENSITY6300", "DIMENSITY6080"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G57"
        ),
        SocSpec(
            displayName = "天玑 6020 / 6100+",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "入门",
            aliases = listOf("DIMENSITY6020"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G57"
        ),
        SocSpec(
            displayName = "天玑 1050 / 1300",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "中高端",
            aliases = listOf("DIMENSITY1050", "DIMENSITY1300"),
            cpuClusters = "4+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G77"
        ),
        SocSpec(
            displayName = "Helio G96 / G100",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "中端",
            aliases = listOf("MT6781", "HELIOG96", "HELIOG100"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G57"
        ),
        SocSpec(
            displayName = "Helio G70 / G50 / G35",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "入门",
            aliases = listOf("MT6769V", "MT6765", "HELIOG70", "HELIOG50", "HELIOG35"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G52"
        ),
        SocSpec(
            displayName = "Helio P60 / P70 / P90",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "入门",
            aliases = listOf("MT6771", "MT6779", "HELIOP60", "HELIOP70", "HELIOP90"),
            cpuClusters = "4+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G72"
        ),
        SocSpec(
            displayName = "Helio P22 / P35 / A22",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "入门",
            aliases = listOf("MT6762", "MT6763", "MT6761", "MT6739", "HELIOP22", "HELIOP35", "HELIOA22"),
            cpuClusters = "4+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "PowerVR GE8320"
        ),

        // ---------- 猎户座（Exynos）补齐 ----------
        SocSpec(
            displayName = "Exynos 1580",
            brandName = "三星 Exynos",
            badgeText = "猎户座",
            performanceLevel = "中高端",
            aliases = listOf("S5E8865", "EXYNOS1580"),
            cpuClusters = "1+3+4",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Xclipse 540"
        ),
        SocSpec(
            displayName = "Exynos 1480",
            brandName = "三星 Exynos",
            badgeText = "猎户座",
            performanceLevel = "中高端",
            aliases = listOf("S5E8885", "EXYNOS1480"),
            cpuClusters = "4+4",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Xclipse 530"
        ),
        SocSpec(
            displayName = "Exynos 1380",
            brandName = "三星 Exynos",
            badgeText = "猎户座",
            performanceLevel = "中端",
            aliases = listOf("S5E8835", "EXYNOS1380"),
            cpuClusters = "4+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G68"
        ),
        SocSpec(
            displayName = "Exynos 1280",
            brandName = "三星 Exynos",
            badgeText = "猎户座",
            performanceLevel = "中端",
            aliases = listOf("S5E8825", "EXYNOS1280"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G68"
        ),
        SocSpec(
            displayName = "Exynos 1080",
            brandName = "三星 Exynos",
            badgeText = "猎户座",
            performanceLevel = "中高端",
            aliases = listOf("EXYNOS1080"),
            cpuClusters = "1+3+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G78"
        ),
        SocSpec(
            displayName = "Exynos 990",
            brandName = "三星 Exynos",
            badgeText = "猎户座",
            performanceLevel = "旗舰",
            aliases = listOf("S5E9830", "EXYNOS990"),
            cpuClusters = "2+2+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G77"
        ),
        SocSpec(
            displayName = "Exynos 9825 / 9820",
            brandName = "三星 Exynos",
            badgeText = "猎户座",
            performanceLevel = "旗舰",
            aliases = listOf("S5E9825", "S5E9820", "EXYNOS9825", "EXYNOS9820"),
            cpuClusters = "2+2+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G76"
        ),
        SocSpec(
            displayName = "Exynos 9810",
            brandName = "三星 Exynos",
            badgeText = "猎户座",
            performanceLevel = "中高端",
            aliases = listOf("S5E9810", "EXYNOS9810"),
            cpuClusters = "4+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G72"
        ),
        SocSpec(
            displayName = "Exynos 9611 / 9610",
            brandName = "三星 Exynos",
            badgeText = "猎户座",
            performanceLevel = "中端",
            aliases = listOf("S5E9611", "S5E9610", "EXYNOS9611", "EXYNOS9610"),
            cpuClusters = "4+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G72"
        ),
        SocSpec(
            displayName = "Exynos 8895 / 8890",
            brandName = "三星 Exynos",
            badgeText = "猎户座",
            performanceLevel = "中高端",
            aliases = listOf("S5E8895", "S5E8890", "EXYNOS8895", "EXYNOS8890"),
            cpuClusters = "4+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G71"
        ),
        SocSpec(
            displayName = "Exynos 7885 / 7904",
            brandName = "三星 Exynos",
            badgeText = "猎户座",
            performanceLevel = "入门",
            aliases = listOf("S5E7885", "S5E7904", "EXYNOS7885", "EXYNOS7904"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G71"
        ),

        // ---------- 麒麟补齐 ----------
        SocSpec(
            displayName = "麒麟 990 5G / 990E",
            brandName = "华为麒麟",
            badgeText = "麒麟",
            performanceLevel = "中高端",
            aliases = listOf("KIRIN990E", "KIRIN9905G", "HI6245"),
            cpuClusters = "2+2+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G76"
        ),
        SocSpec(
            displayName = "麒麟 655 / 659 / 650",
            brandName = "华为麒麟",
            badgeText = "麒麟",
            performanceLevel = "入门",
            aliases = listOf("HI6250", "HI6250M", "KIRIN655", "KIRIN659", "KIRIN650"),
            cpuClusters = "4+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-T830"
        ),
        SocSpec(
            displayName = "麒麟 710A / 710F",
            brandName = "华为麒麟",
            badgeText = "麒麟",
            performanceLevel = "入门",
            aliases = listOf("KIRIN710A", "KIRIN710F"),
            cpuClusters = "4+4",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G51"
        ),

        // ---------- 紫光展锐补齐 ----------
        SocSpec(
            displayName = "紫光展锐 T310 / SC9863A 系列",
            brandName = "紫光展锐 / 展讯",
            badgeText = "展锐",
            performanceLevel = "入门",
            aliases = listOf("UMS312", "T310", "SC9863A1", "SC9863A2"),
            cpuClusters = "4 核同频",
            cpuArchitecture = "ARM64",
            gpuName = "PowerVR GE8322"
        ),
        SocSpec(
            displayName = "紫光展锐 SC9820E / SC7731E",
            brandName = "紫光展锐 / 展讯",
            badgeText = "展锐",
            performanceLevel = "入门",
            aliases = listOf("SC9820E", "SC7731E", "SPRD9820E"),
            cpuClusters = "4 核同频",
            cpuArchitecture = "ARM64",
            gpuName = "Mali-T820"
        ),
        SocSpec(
            displayName = "紫光展锐 T9100",
            brandName = "紫光展锐 / 展讯",
            badgeText = "展锐",
            performanceLevel = "中高端",
            aliases = listOf("T9100", "UMS9620A"),
            cpuClusters = "1+3+4",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "未公开"
        )
    )

    fun normalize(input: String): String {
        return input.uppercase(Locale.getDefault())
            .replace("QUALCOMM", "")
            .replace("SNAPDRAGON", "SNAPDRAGON")
            .replace("MEDIATEK", "")
            .replace("DIMENSITY", "DIMENSITY")
            .replace("HELIO", "HELIO")
            .replace("SAMSUNG", "")
            .replace("EXYNOS", "EXYNOS")
            .replace("HISILICON", "")
            .replace("KIRIN", "KIRIN")
            .replace("UNISOC", "")
            .replace("SPREADTRUM", "")
            .replace("_", "")
            .replace("-", "")
            .replace(" ", "")
            .trim()
    }

    fun findBestSpec(
        candidates: List<String>,
        deviceHints: List<String>,
        gpuMaxFreqMHz: Int?
    ): SocSpec? {
        val normalizedCandidates = candidates.map { normalize(it) }.filter { it.isNotBlank() }
        val normalizedDeviceHints = deviceHints.map { it.uppercase(Locale.getDefault()) }

        var bestSpec: SocSpec? = null
        var bestScore = Int.MIN_VALUE

        for (spec in specs) {
            var score = 0

            for (alias in spec.aliases) {
                val nAlias = normalize(alias)
                for (candidate in normalizedCandidates) {
                    when {
                        // 完全相等时按别名长度加权：越具体的别名（例如 DIMENSITY7050）越优先于共用代号（MT6877）
                        candidate == nAlias -> score += EXACT_MATCH_SCORE + nAlias.length
                        // 极短别名（例如玄戒的 "O1"）只认精确相等，避免误匹配到别的型号
                        nAlias.length >= MIN_PARTIAL_MATCH_LENGTH &&
                            (candidate.contains(nAlias) || nAlias.contains(candidate)) ->
                            score += PARTIAL_MATCH_SCORE + nAlias.length
                    }
                }
            }

            for (kw in spec.deviceKeywords) {
                for (hint in normalizedDeviceHints) {
                    if (hint.contains(kw)) score += 120
                }
            }

            if (spec.displayName == "Snapdragon 860" && gpuMaxFreqMHz != null && gpuMaxFreqMHz >= 670) {
                score += 20
            }
            if (spec.displayName == "Snapdragon 855" && gpuMaxFreqMHz != null && gpuMaxFreqMHz in 560..620) {
                score += 15
            }

            if (score > bestScore) {
                bestScore = score
                bestSpec = spec
            }
        }

        return if (bestScore >= MATCH_THRESHOLD) bestSpec else null
    }

    /** 部分匹配要求别名至少有 4 个字符。 */
    private const val MIN_PARTIAL_MATCH_LENGTH = 4

    private const val EXACT_MATCH_SCORE = 100

    private const val PARTIAL_MATCH_SCORE = 45

    /** 达到该分数才认为匹配成功。 */
    private const val MATCH_THRESHOLD = 45
}
