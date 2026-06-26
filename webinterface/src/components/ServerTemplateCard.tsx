import {launchServer, type ServerTemplate, usePersistentNavigate} from "../lib/api.ts";
import styles from "./ServerTemplateCard.module.css";
import ImageButton from "./ImageButton.tsx";
import {FaCirclePlay, FaRegCirclePlay} from "react-icons/fa6";
import {useI18n} from "../lib/i18n.ts";

type ServerTemplateCardProps = ServerTemplate & {
    canOpen: boolean;
    canLaunch: boolean;
}

function ServerTemplateCard({ name, server_software, version, canOpen, canLaunch }: ServerTemplateCardProps){
    const navigate = usePersistentNavigate();
    const {t} = useI18n();

    function openServer(serverName: string) {
        navigate(`/server/${encodeURIComponent(serverName)}`);
    }

    async function startSingleton(e : React.MouseEvent<HTMLButtonElement>){
        e.stopPropagation();
        const serverName : string = await launchServer(name, true);
        openServer(serverName);
    }
    async function startInstance(e : React.MouseEvent<HTMLButtonElement>) {
        e.stopPropagation();
        const serverName : string = await launchServer(name, false);
        openServer(serverName);
    }

    function open(){
        if (!canOpen) return;
        navigate(`/templates/${encodeURIComponent(name)}`)
    }
    return (
        <div className={`${styles.card} ${!canOpen ? styles.disabled : ""}`} onClick={open}>
            <h3>{name}</h3>
            <span>{t("field.software")}: {server_software}</span><br/>
            <span>{t("field.version")}: {version}</span><br/>
            {canLaunch && (
                <div className={styles.buttonList}>
                    <ImageButton onClick={startSingleton} tooltip={t("action.launch_singleton")}><FaRegCirclePlay/></ImageButton>
                    <ImageButton onClick={startInstance} tooltip={t("action.launch_instance")}><FaCirclePlay/></ImageButton>
                </div>
            )}
        </div>
    );
}

export default ServerTemplateCard;
