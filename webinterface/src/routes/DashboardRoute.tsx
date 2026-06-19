import styles from "./DashboardRoute.module.css"

import LineChart from "../components/LineChart.tsx"
import {useProxyMetrics} from "../hooks/useProxyMetrics.ts";




function DashboardRoute(){
    const { playerCountData, networkData, error } = useProxyMetrics("days", "days");

    return (
        <>
            <h1>Dashboard</h1>
            {error && <p role={"alert"}>{error}</p>}
            <div className={styles.charts}>
                <LineChart
                    data={playerCountData}
                    keyName={"Online"}
                    title={"Playercount"}
                    width={500}
                    height={300}
                />
                <LineChart
                    data={networkData}
                    dataKey={"inbound"}
                    keyName={"Inbound (MB)"}
                    additionalLines={[
                        {
                            dataKey: "outbound",
                            name: "Outbound (MB)",
                            color: "#22c55e"
                        }
                    ]}
                    title={"Network"}
                    width={500}
                    height={300}
                />
            </div>
        </>
    );
}
export default DashboardRoute;
