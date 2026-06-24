import LiveConsole from "../components/LiveConsole.tsx";
import styles from "./ProxyRoute.module.css"
import LineChart from "../components/LineChart.tsx";
import {useProxyMetrics} from "../hooks/useProxyMetrics.ts";


function ProxyRoute(){
    const { playerCountData, networkData, error } = useProxyMetrics("minutes", "minutes");

    return (
        <>
            <div className={styles.proxy}>
                <div className={styles.console}>
                    <LiveConsole consoleId={"proxy"}/>
                </div>
                <div className={styles.stats}>
                    {error && <p role={"alert"}>{error}</p>}
                    <LineChart
                        data={playerCountData}
                        keyName={"Online"}
                        title={"Playercount"}
                        width={350}
                        height={175}
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
                        width={350}
                        height={175}
                    />
                </div>
            </div>
        </>
    )
}

export default ProxyRoute;
