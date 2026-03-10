package com.example.vstmobile.utils

import com.example.vstmobile.services.PCPProgramacaoItem
import java.text.SimpleDateFormat
import java.util.*

/**
 * Builds the HTML for the Programação PCP PDF report.
 * Uses the exact same HTML structure as the provided programacao_pcp.html template.
 */
fun buildProgramacaoHtml(
    data: List<PCPProgramacaoItem>,
    dtIni: String,   // dd/MM/yyyy
    dtFim: String,   // dd/MM/yyyy
    linhaFiltro: String = "Todas as linhas"
): String {
    val now = SimpleDateFormat("dd/MM/yyyy - HH:mm:ss", Locale("pt", "BR")).format(Date())

    // Group by LP -> day (sorted LP asc, then date asc)
    val lpGroups = data.groupBy { "${it.idLinhaProducaoPosse}" to it.descLinhaProducao }
        .toSortedMap(compareBy { it.first.toIntOrNull() ?: 0 })

    val sb = StringBuilder()
    sb.append("""<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="UTF-8">
<style>
@page { size: A4 portrait; margin: 10mm 12mm; }
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: Arial, Helvetica, sans-serif; font-size: 9pt; color: #000; }
table.hdr { width: 100%; border-collapse: collapse; margin-bottom: 4px; }
.hdr-left  { width: 22%; vertical-align: middle; }
.hdr-center{ text-align: center; vertical-align: middle; font-size: 8.5pt; line-height: 1.7; }
.hdr-right { text-align: right; font-size: 8pt; vertical-align: top; color: #444; line-height: 1.7; width: 22%; }
.brand     { font-size: 18pt; font-weight: 900; color: #003087; letter-spacing: -1px; }
.brand-sub { font-size: 7pt; color: #555; }
.report-main-title { font-size: 12pt; font-weight: bold; letter-spacing: 3px; margin-bottom: 2px; }
hr.hdr-rule { border: none; border-top: 2px solid #000; margin: 4px 0 8px 0; }
.lp  { background: #1a1a1a; color: #fff; padding: 5px 10px; font-size: 10pt; font-weight: bold; margin-top: 12px; }
.dayh{ background: #555;    color: #fff; padding: 3px 10px; font-size: 9pt; font-weight: bold; margin-top: 4px; }
table { width: 100%; border-collapse: collapse; }
thead tr { background: #888; color: #fff; }
th  { padding: 3px 7px; font-size: 8pt; text-align: left; white-space: nowrap; }
td  { padding: 2.5px 7px; font-size: 8pt; border-bottom: 1px solid #e4e4e4; }
tr:nth-child(even) td { background: #f7f7f7; }
.c  { text-align: center; }
.r  { text-align: right; }
.tdia  { display: flex; justify-content: flex-end; gap: 20px; padding: 3px 8px; font-size: 8.5pt; font-weight: bold; background: #ebebeb; border-top: 1px solid #aaa; }
.tlinha{ display: flex; justify-content: space-between; align-items: center; background: #333; color: #fff; padding: 6px 10px; font-size: 10pt; font-weight: bold; margin-top: 4px; }
.tgeral{ display: flex; justify-content: flex-end; gap: 24px; padding: 8px 10px; font-size: 13pt; font-weight: bold; border-top: 3px solid #000; margin-top: 10px; }
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
      <div class='report-main-title'>PROGRAMAÇÃO PCP</div>
      <div>Período: $dtIni a $dtFim</div>
      <div>Linha: $linhaFiltro</div>
    </td>
    <td class='hdr-right'><div>Data/Hora: $now</div></td>
  </tr>
</table>
<hr class='hdr-rule'>
""")

    var totalGeral = 0.0

    for ((lpKey, lpItems) in lpGroups) {
        val (lpId, lpDesc) = lpKey
        sb.append("\n<div class='lp'>LP: $lpId - $lpDesc</div>\n")

        // Group by day within LP
        val dayGroups = lpItems.groupBy { progFormatDate(it.dtEncerramento) }
            .toSortedMap()

        var lpTotal = 0.0

        for ((day, dayItems) in dayGroups) {
            sb.append("\n<div class='dayh'>DIA DA PROGRAMAÇÃO: $day</div>\n")
            sb.append("""<table>
  <thead>
    <tr>
      <th>PRODUTO</th>
      <th>CLIENTE</th>
      <th>VENDEDOR</th>
      <th>PV / OP</th>
      <th class='c'>DATA</th>
      <th class='c'>FATOR</th>
      <th class='r'>QUANTIDADE</th>
    </tr>
  </thead>
  <tbody>
""")

            var dayTotal = 0.0

            dayItems.forEach { item ->
                val pvOp = item.idOp.ifBlank { item.idSaida }
                val um   = item.abreviatura.ifBlank { "UN" }
                val fator = if (item.pesoProdutoCad > 0) item.pesoProdutoCad.toInt().toString() else "1"

                sb.append("    <tr>")
                sb.append("<td>${escapeHtml(item.descProduto)}</td>")
                sb.append("<td>${escapeHtml(item.cliente)}</td>")
                sb.append("<td>${escapeHtml(item.nomeVendedor)}</td>")
                sb.append("<td>$pvOp</td>")
                sb.append("<td class='c'>$day</td>")
                sb.append("<td class='c'>$fator</td>")
                sb.append("<td class='r'>${progFmtNum(item.qtdProduzir)} $um</td>")
                sb.append("</tr>\n")

                dayTotal += item.qtdProduzir
            }

            sb.append("  </tbody>\n</table>\n")
            val um = dayItems.firstOrNull()?.abreviatura ?: "UN"
            sb.append("<div class='tdia'>\n")
            sb.append("  <span>TOTAL DO DIA:</span>\n")
            sb.append("  <span>${progFmtNum(dayTotal)} $um</span>\n")
            sb.append("</div>\n")

            lpTotal += dayTotal
        }

        val um = lpItems.firstOrNull()?.abreviatura ?: "UN"
        sb.append("\n<div class='tlinha'>\n")
        sb.append("  <span>TOTAL DA LINHA ($lpId-$lpDesc) :</span>\n")
        sb.append("  <span>${progFmtNum(lpTotal)} $um</span>\n")
        sb.append("</div>\n")

        totalGeral += lpTotal
    }

    val um = data.firstOrNull()?.abreviatura ?: "UN"
    sb.append("\n<div class='tgeral'>\n")
    sb.append("  <span>TOTAL GERAL :</span>\n")
    sb.append("  <span>${progFmtNum(totalGeral)} $um</span>\n")
    sb.append("</div>\n\n</body>\n</html>")

    return sb.toString()
}

// ── Helpers ────────────────────────────────────────────────────────────────────

private fun progFormatDate(isoDate: String): String {
    if (isoDate.isBlank()) return "Sem data"
    return try {
        val raw = isoDate.substring(0, 10) // "2026-03-15"
        val parts = raw.split("-")
        "${parts[2]}/${parts[1]}/${parts[0]}"
    } catch (e: Exception) {
        isoDate
    }
}

private fun progFmtNum(value: Double): String {
    if (value == 0.0) return "0"
    return String.format(Locale("pt", "BR"), "%,.0f", value)
}

private fun escapeHtml(text: String): String {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

