import {useEffect, useState} from "react";
import ServerCard from "../components/ServerCard.tsx";
import {getRunningServers, type Server} from "../lib/api.ts";

import styles from "./ServerListRoute.module.css";

function ServerListRoute(){
    const [servers, setServers] = useState<Server[]>([]);

    useEffect(() => {
        getRunningServers().then(setServers);
    }, []);

    return (
        <>
            <h1>Servers</h1>
            <div className={styles.list}>
                {servers.map((server) => (
                    <ServerCard key={server.name} {...server}/>
                ))}
            </div>
        </>
    );
}

export default ServerListRoute;
