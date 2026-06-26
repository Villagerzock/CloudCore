import styles from "./DashboardRoute.module.css"

import LineChart from "../components/LineChart.tsx"
import {useProxyMetrics} from "../hooks/useProxyMetrics.ts";
import {useI18n} from "../lib/i18n.ts";




function DashboardRoute(){
    const { playerCountData, networkData, error } = useProxyMetrics("days", "days");
    const {t} = useI18n();

    return (
        <>
            <h1>{t("page.dashboard")}</h1>
            {error && <p role={"alert"}>{error}</p>}
            <div className={styles.charts}>
                <LineChart
                    data={playerCountData}
                    keyName={t("metrics.online")}
                    title={t("metrics.playercount")}
                    width={500}
                    height={300}
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
                    width={500}
                    height={300}
                />
            </div>
        </>
    );
}
export default DashboardRoute;
