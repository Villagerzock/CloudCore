import {useEffect, useState} from "react";
import ServerCard from "../components/ServerCard.tsx";
import {getRunningServers, type Server} from "../lib/api.ts";

import styles from "./ServerListRoute.module.css";
import {useI18n} from "../lib/i18n.ts";

function ServerListRoute(){
    const [servers, setServers] = useState<Server[]>([]);
    const {t} = useI18n();

    useEffect(() => {
        getRunningServers().then(setServers);
    }, []);

    return (
        <>
            <h1>{t("page.servers")}</h1>
            <div className={styles.list}>
                {servers.map((server) => (
                    <ServerCard key={server.name} {...server}/>
                ))}
            </div>
        </>
    );
}

export default ServerListRoute;
