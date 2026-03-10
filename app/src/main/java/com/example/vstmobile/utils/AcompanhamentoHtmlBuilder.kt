package com.example.vstmobile.utils

import com.example.vstmobile.services.PCPAcompanhamentoItem
import java.text.SimpleDateFormat
import java.util.*

/**
 * Builds the HTML for the Acompanhamento PCP PDF report.
 * Uses the exact same HTML structure as the provided acompanhamento_pcp.html template.
 */
fun buildAcompanhamentoHtml(
    data: List<PCPAcompanhamentoItem>,
    dtIni: String,   // dd/MM/yyyy
    dtFim: String,   // dd/MM/yyyy
    linhaFiltro: String = "Todas as linhas"
): String {
    val now = SimpleDateFormat("dd/MM/yyyy - HH:mm:ss", Locale("pt", "BR")).format(Date())

    // Group by LP -> day
    val lpGroups = data.groupBy { "${it.idLinhaProducaoPosse}" to it.descLinhaProducao }
        .toSortedMap(compareBy { it.first.toIntOrNull() ?: 0 })

    val sb = StringBuilder()
    sb.append("""<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="UTF-8">
<style>
@page { size: A4 landscape; margin: 10mm 12mm; }
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: Arial, Helvetica, sans-serif; font-size: 8.5pt; color: #000; }
table.hdr { width: 100%; border-collapse: collapse; margin-bottom: 4px; }
.hdr-left  { width: 22%; vertical-align: middle; }
.hdr-center{ text-align: center; vertical-align: middle; font-size: 8.5pt; line-height: 1.7; }
.hdr-right { text-align: right; font-size: 8pt; vertical-align: top; color: #444; line-height: 1.7; width: 22%; }
.brand     { font-size: 18pt; font-weight: 900; color: #003087; letter-spacing: -1px; }
.brand-sub { font-size: 7pt; color: #555; }
.report-main-title { font-size: 12pt; font-weight: bold; letter-spacing: 3px; margin-bottom: 2px; }
hr.hdr-rule { border: none; border-top: 2px solid #000; margin: 4px 0 8px 0; }
.lp { background: #1a1a1a; color: #fff; padding: 5px 10px; font-size: 10pt; font-weight: bold; margin-top: 12px; }
table { width: 100%; border-collapse: collapse; margin-top: 0; }
thead tr { background: #666; color: #fff; }
th { padding: 3px 6px; font-size: 7.5pt; text-align: left; white-space: nowrap; }
td { padding: 2px 6px; font-size: 7.5pt; border-bottom: 1px solid #e8e8e8; }
tr.alt td { background: #f5f5f5; }
tr.dayr td { background: #888; color: #fff; font-weight: bold; font-size: 8pt; padding: 3px 6px; }
tr.tdia td { background: #e0e0e0; font-weight: bold; border-top: 1px solid #999; }
.c { text-align: center; }
.r { text-align: right; }
.tlinha { display: flex; justify-content: space-between; align-items: center; background: #333; color: #fff; padding: 5px 10px; font-size: 9pt; font-weight: bold; margin-top: 2px; }
.tgeral { display: flex; justify-content: space-between; align-items: center; padding: 8px 10px; font-size: 11pt; font-weight: bold; border-top: 3px solid #000; margin-top: 10px; }
</style>
</head>
<body>

<!-- HEADER -->
<table class='hdr'>
  <tr>
    <td class='hdr-left'>
      <div class='brand'>FERMAT</div>
      <div class='brand-sub'>Ind. e Com. de Perfis</div>
    </td>
    <td class='hdr-center'>
      <div class='report-main-title'>ACOMPANHAMENTO PCP</div>
      <div>Período: $dtIni a $dtFim</div>
      <div>Linha: $linhaFiltro</div>
    </td>
    <td class='hdr-right'><div>Data/Hora: $now</div></td>
  </tr>
</table>
<hr class='hdr-rule'>
""")

    var totalGeralQtd = 0.0
    var totalGeralM2  = 0.0
    var totalGeralKg  = 0.0
    var totalGeralCount = 0

    for ((lpKey, lpItems) in lpGroups) {
        val (lpId, lpDesc) = lpKey
        sb.append("\n<div class='lp'>LP: $lpId - $lpDesc</div>\n")
        sb.append("""<table>
  <thead>
    <tr>
      <th>Ped/OP</th><th>Produto</th>
      <th class='c'>Data</th><th class='c'>UM</th>
      <th class='r'>Qtd.(UN)</th><th class='r'>m²/m³</th><th class='r'>KG</th>
    </tr>
  </thead>
  <tbody>
""")

        // Group by day within LP
        val dayGroups = lpItems.groupBy { formatDate(it.dtEncerramento) }
            .toSortedMap()

        var lpTotalQtd = 0.0
        var lpTotalM2  = 0.0
        var lpTotalKg  = 0.0
        var lpCount    = 0

        for ((day, dayItems) in dayGroups) {
            sb.append("    <tr class='dayr'><td colspan='7'>$day</td></tr>\n")

            var dayTotalQtd = 0.0
            var dayTotalM2  = 0.0
            var dayTotalKg  = 0.0

            dayItems.forEachIndexed { idx, item ->
                val pedOp = item.idOp.ifBlank { item.idSaida }
                val um    = item.abreviatura.ifBlank { "UN" }
                val kg    = item.qtdProduzida * item.pesoProdutoCad
                val altClass = if (idx % 2 == 1) " class='alt'" else ""

                sb.append("    <tr$altClass>")
                sb.append("<td>$pedOp</td>")
                sb.append("<td>${item.descProduto}</td>")
                sb.append("<td class='c'>$day</td>")
                sb.append("<td class='c'>$um</td>")
                sb.append("<td class='r'>${fmtNum(item.qtdProduzida)}</td>")
                sb.append("<td class='r'>${fmtNum(item.qtdTotalLp)}</td>")
                sb.append("<td class='r'>${fmtNum(kg)} KG</td>")
                sb.append("</tr>\n")

                dayTotalQtd += item.qtdProduzida
                dayTotalM2  += item.qtdTotalLp
                dayTotalKg  += kg
            }

            sb.append("    <tr class='tdia'>\n")
            sb.append("      <td colspan='4'>TOTAL DO DIA: [${String.format("%04d", dayItems.size)}]</td>\n")
            sb.append("      <td class='r'>${fmtNum(dayTotalQtd)} ${lpItems.firstOrNull()?.abreviatura ?: "UN"}</td>\n")
            sb.append("      <td class='r'>${fmtNum(dayTotalM2)}</td>\n")
            sb.append("      <td class='r'>${fmtNum(dayTotalKg)} KG</td>\n")
            sb.append("    </tr>\n")

            lpTotalQtd += dayTotalQtd
            lpTotalM2  += dayTotalM2
            lpTotalKg  += dayTotalKg
            lpCount    += dayItems.size
        }

        sb.append("  </tbody>\n</table>\n\n")

        val um = lpItems.firstOrNull()?.abreviatura ?: "UN"
        sb.append("<div class='tlinha'>\n")
        sb.append("  <span>TOTAL DA LP ($lpId-$lpDesc) :</span>\n")
        sb.append("  <span>${fmtNum(lpTotalQtd)} $um &nbsp;|&nbsp; ${fmtNum(lpTotalM2)} m²/m³ &nbsp;|&nbsp; ${fmtNum(lpTotalKg)} KG</span>\n")
        sb.append("</div>\n")

        totalGeralQtd   += lpTotalQtd
        totalGeralM2    += lpTotalM2
        totalGeralKg    += lpTotalKg
        totalGeralCount += lpCount
    }

    sb.append("\n<div class='tgeral'>\n")
    sb.append("  <span>TOTAL GERAL PRODUZIDO: [${String.format("%04d", totalGeralCount)}]</span>\n")
    sb.append("  <span>${fmtNum(totalGeralQtd)} ${data.firstOrNull()?.abreviatura ?: "UN"} &nbsp;|&nbsp; ${fmtNum(totalGeralM2)} m²/m³ &nbsp;|&nbsp; ${fmtNum(totalGeralKg)} KG</span>\n")
    sb.append("</div>\n\n</body>\n</html>")

    return sb.toString()
}

// ── Helpers ────────────────────────────────────────────────────────────────────

private fun formatDate(isoDate: String): String {
    if (isoDate.isBlank()) return "Sem data"
    return try {
        // Handle ISO format: "2026-03-08T00:00:00.000Z"
        val raw = isoDate.substring(0, 10) // "2026-03-08"
        val parts = raw.split("-")
        "${parts[2]}/${parts[1]}/${parts[0]}"
    } catch (e: Exception) {
        isoDate
    }
}

private fun fmtNum(value: Double): String {
    if (value == 0.0) return "0"
    return String.format(Locale("pt", "BR"), "%,.2f", value)
}

