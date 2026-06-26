import LiveConsole from "../components/LiveConsole.tsx";
import styles from "./ProxyRoute.module.css"
import LineChart from "../components/LineChart.tsx";
import {useProxyMetrics} from "../hooks/useProxyMetrics.ts";
import {useI18n} from "../lib/i18n.ts";


function ProxyRoute(){
    const { playerCountData, networkData, error } = useProxyMetrics("minutes", "minutes");
    const {t} = useI18n();

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
                        keyName={t("metrics.online")}
                        title={t("metrics.playercount")}
                        width={350}
                        height={175}
                    />
                    <LineChart
                        data={networkData}
                        dataKey={"inbound"}
                        keyName={t("metrics.inbound_mb")}
                        additionalLines={[
                            {
                                dataKey: "outbound",
                                name: t("metrics.outbound_mb"),
                                color: "#22c55e"
                            }
                        ]}
                        title={t("metrics.network")}
                        width={350}
                        height={175}
                    />
                </div>
            </div>
        </>
    )
}

export default ProxyRoute;
