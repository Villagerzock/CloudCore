import {launchServer, type ServerTemplate} from "../lib/api.ts";
import styles from "./ServerTemplateCard.module.css";
import ImageButton from "./ImageButton.tsx";
import {FaCirclePlay, FaRegCirclePlay} from "react-icons/fa6";
import {useLocation, useNavigate} from "react-router";

function ServerTemplateCard({ name, server_software, version }: ServerTemplate){
    const navigate = useNavigate();
    const location = useLocation();

    function openServer(serverName: string) {
        navigate({
            pathname: `/server/${encodeURIComponent(serverName)}`,
            search: location.search
        });
    }

    async function startSingleton(){
        const serverName : string = await launchServer(name, true);
        openServer(serverName);
    }
    async function startInstance() {
        const serverName : string = await launchServer(name, false);
        openServer(serverName);
    }
    return (
        <div className={styles.card}>
            <h3>{name}</h3>
            <span>Software: {server_software}</span><br/>
            <span>Version: {version}</span><br/>
            <div className={styles.buttonList}>
                <ImageButton onClick={startSingleton} tooltip={"Launch Singleton"}><FaRegCirclePlay/></ImageButton>
                <ImageButton onClick={startInstance} tooltip={"Launch Instance"}><FaCirclePlay/></ImageButton>
            </div>
        </div>
    );
}

export default ServerTemplateCard;
