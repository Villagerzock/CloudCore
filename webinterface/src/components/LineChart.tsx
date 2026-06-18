import {type CSSProperties, useState} from "react";
import {CartesianGrid, Line, LineChart as Chart, ResponsiveContainer, Tooltip, XAxis, YAxis} from "recharts";
import styles from "./LineChart.module.css";
import type {ChartData} from "../lib/api.ts";

type LineChartProps = {
    data: ChartData[];
    dataKey?: string;
    keyName: string;
    color?: string;
    additionalLines?: AdditionalLine[];
    title?: string;
    width?: CSSProperties["width"];
    height?: CSSProperties["height"];
}



type AdditionalLine = {
    dataKey: string;
    name: string;
    color: string;
}

function toCssSize(size: CSSProperties["width"]){
    return typeof size === "number" ? `${size}px` : size;
}

function LineChart ({
    data,
    dataKey = "value",
    keyName,
    color = "var(--accent)",
    additionalLines = [],
    title,
    width,
    height
}: LineChartProps){

    const [animate, setAnimate] = useState<boolean>(true);

    const chartSize = {
        "--chart-width": toCssSize(width),
        "--chart-height": toCssSize(height)
    } as CSSProperties;

    return <div className={styles.chartCard} style={chartSize}>
        {title && (
            <h2 className={styles.title}>
                {title}
            </h2>
        )}
        <div className={styles.chartArea}>
            <ResponsiveContainer width={"100%"} height={"100%"}>
                <Chart data={data} margin={{ top: 20, right: 28, bottom: 10, left: 0 }}>
                    <CartesianGrid stroke={"var(--border)"} strokeDasharray={"4 8"} vertical={false}/>
                    <XAxis
                        dataKey={"key"}
                        axisLine={false}
                        tickLine={false}
                        tickMargin={14}
                        tick={{ fill: "var(--text)", fontSize: 12 }}
                        tickFormatter={(date) => date.slice(0, 5)}
                    />
                    <YAxis
                        axisLine={false}
                        tickLine={false}
                        tickMargin={12}
                        tick={{ fill: "var(--text)", fontSize: 12 }}
                    />
                    <Tooltip
                        cursor={{ stroke: "var(--accent)", strokeOpacity: 0.18, strokeWidth: 2 }}
                        contentStyle={{
                            background: "var(--bg)",
                            border: "1px solid var(--border)",
                            borderRadius: 8,
                            boxShadow: "var(--shadow)",
                            color: "var(--text-h)"
                        }}
                        labelStyle={{ color: "var(--text)", marginBottom: 4 }}
                    />
                    <Line
                        type={"monotone"}
                        dataKey={dataKey}
                        stroke={color}
                        strokeWidth={3}
                        dot={false}
                        activeDot={false}
                        name={keyName}
                        isAnimationActive={animate}
                        onAnimationEnd={()=> setAnimate(false)}
                    />
                    {additionalLines.map((line) => (
                        <Line
                            key={line.dataKey}
                            type={"monotone"}
                            dataKey={line.dataKey}
                            stroke={line.color}
                            strokeWidth={3}
                            dot={false}
                            activeDot={false}
                            name={line.name}
                            isAnimationActive={animate}
                        />
                    ))}
                </Chart>
            </ResponsiveContainer>
        </div>
    </div>
}

export default LineChart;
