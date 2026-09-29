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
            displayName = "Snapdragon 8 Elite",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "顶级旗舰",
            aliases = listOf("SM8750", "SNAPDRAGON8ELITE"),
            cpuClusters = "2+6 自研 Oryon",
            cpuArchitecture = "ARM64 / Oryon",
            gpuName = "Adreno 830",
            gpuCores = "未公开",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "Snapdragon 8 Gen 3",
            brandName = "高通骁龙",
            badgeText = "骁龙",
            performanceLevel = "顶级旗舰",
            aliases = listOf("SM8650", "SNAPDRAGON8GEN3"),
            cpuClusters = "1×3.30 + 5×3.20 + 2×2.30 GHz",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Adreno 750",
            gpuCores = "未公开",
            graphicsApi = "OpenGL ES 3.2 / Vulkan 1.3"
        ),
        SocSpec(
            displayName = "Snapdragon 8s Gen 3",
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
            displayName = "Snapdragon 8 Gen 2",
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
            displayName = "Snapdragon 8+ Gen 1",
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
            displayName = "Snapdragon 8 Gen 1",
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
            displayName = "Snapdragon 888",
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
            displayName = "Snapdragon 870",
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
            displayName = "Snapdragon 865+",
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
            displayName = "Snapdragon 865",
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
            displayName = "Snapdragon 860",
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
            displayName = "Snapdragon 855+",
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
            displayName = "Snapdragon 855",
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
            displayName = "Snapdragon 845",
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
            displayName = "Dimensity 9400",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "顶级旗舰",
            aliases = listOf("MT6991", "DIMENSITY9400"),
            cpuClusters = "全大核架构",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Immortalis-G925",
            gpuCores = "12 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "Dimensity 9300",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "顶级旗舰",
            aliases = listOf("MT6989", "DIMENSITY9300"),
            cpuClusters = "全大核架构",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Immortalis-G720",
            gpuCores = "12 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "Dimensity 9200+",
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
            displayName = "Dimensity 9000 / 9000+",
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
            displayName = "Dimensity 8300",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "中高端",
            aliases = listOf("MT6897", "DIMENSITY8300"),
            cpuClusters = "4+4",
            cpuArchitecture = "ARMv9 / AArch64",
            gpuName = "Mali-G615",
            gpuCores = "6 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "Dimensity 8200",
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
            displayName = "Dimensity 7200 / 7300",
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
            displayName = "Dimensity 7050 / 6100+",
            brandName = "联发科",
            badgeText = "联发科",
            performanceLevel = "中端",
            aliases = listOf("MT6877", "MT6877V", "DIMENSITY7050", "DIMENSITY6100"),
            cpuClusters = "2+6",
            cpuArchitecture = "ARMv8 / AArch64",
            gpuName = "Mali-G68",
            gpuCores = "4 核",
            graphicsApi = "OpenGL ES / Vulkan"
        ),
        SocSpec(
            displayName = "Dimensity 930 / 1080",
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
            displayName = "Dimensity 700 / 810",
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
            displayName = "Kirin 9020",
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
            displayName = "Kirin 9010",
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
            displayName = "Kirin 9000S",
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
            displayName = "Kirin 9000 / 9000E",
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
            displayName = "Kirin 990",
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
            displayName = "Kirin 980",
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
            displayName = "Kirin 970",
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
            displayName = "UNISOC T820",
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
            displayName = "UNISOC T770",
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
            displayName = "UNISOC T760",
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
            displayName = "UNISOC T618",
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
            displayName = "UNISOC T616",
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
            displayName = "UNISOC T610",
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
            displayName = "UNISOC SC9863A",
            brandName = "紫光展锐 / 展讯",
            badgeText = "展锐",
            performanceLevel = "入门",
            aliases = listOf("SC9863A"),
            cpuClusters = "8 核同频",
            cpuArchitecture = "ARM64",
            gpuName = "PowerVR GE8322",
            graphicsApi = "OpenGL ES / Vulkan"
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
                    if (candidate == nAlias) score += 100
                    else if (candidate.contains(nAlias) || nAlias.contains(candidate)) score += 45
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

        return if (bestScore >= 45) bestSpec else null
    }
}
