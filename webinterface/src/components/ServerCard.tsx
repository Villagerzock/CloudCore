import type {Server} from "../lib/api.ts";
import styles from "./ServerCard.module.css";
import {useLocation, useNavigate} from "react-router";
import {useI18n} from "../lib/i18n.ts";


function ServerCard({ name, template, online, max }: Server){
    const navigator = useNavigate();
    const location = useLocation();
    const {t} = useI18n();
    function navigate(){
        navigator({pathname: `/server/${encodeURIComponent(name)}`, search: location.search})
    }
    return (
        <div className={styles.card} onClick={navigate}>
            <h3>{name}</h3>
            <span>{t("field.template")}: {template}</span><br/>
            <span>{t("metrics.online")}: {online}/{max}</span><br/>
        </div>
    )
}

export default ServerCard;
