package com.sedulimasbot;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

public class ChartGenerator {
    
    // Повертає URL зображення, згенерованого сервісом QuickChart
    public static String getChartUrl(String currency, List<Double> rates) {
        if (rates == null || rates.isEmpty()) return null;

        StringBuilder dataStr = new StringBuilder("[");
        StringBuilder labelsStr = new StringBuilder("[");
        for (int i = 0; i < rates.size(); i++) {
            dataStr.append(rates.get(i));
            labelsStr.append("'Dzień ").append(i + 1).append("'");
            if (i < rates.size() - 1) {
                dataStr.append(",");
                labelsStr.append(",");
            }
        }
        dataStr.append("]");
        labelsStr.append("]");

        double min = Collections.min(rates);
        double max = Collections.max(rates);
        double padding = (max - min) * 0.1; 
        if (padding == 0) padding = 0.05;

        // Конфігурація для Chart.js, яку обробить QuickChart.io
        String chartConfig = "{"
            + "type:'line',"
            + "data:{"
            + "labels:" + labelsStr.toString() + ","
            + "datasets:[{"
            + "label:'Kurs " + currency + " (w PLN)',"
            + "data:" + dataStr.toString() + ","
            + "borderColor:'rgb(54, 162, 235)',"
            + "backgroundColor:'rgba(54, 162, 235, 0.2)',"
            + "fill:true,"
            + "borderWidth: 2,"
            + "pointRadius: 0"
            + "}]},"
            + "options:{"
            + "scales:{"
            + "yAxes:[{ticks:{min:" + (min - padding) + ",max:" + (max + padding) + "}}]"
            + "},"
            + "legend:{display:true, position:'bottom'}"
            + "}"
            + "}";

        try {
            String encodedConfig = URLEncoder.encode(chartConfig, StandardCharsets.UTF_8.toString());
            // Сервіс сам відмалює картинку графіку
            return "https://quickchart.io/chart?c=" + encodedConfig + "&w=600&h=300&bkg=white";
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}