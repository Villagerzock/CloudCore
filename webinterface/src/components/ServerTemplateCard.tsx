import type {ServerTemplate} from "../lib/api.ts";
import styles from "./ServerTemplateCard.module.css";

function ServerTemplateCard({ name, server_software, version }: ServerTemplate){
    return (
        <div className={styles.card}>
            <h3>{name}</h3>
            <span>Software: {server_software}</span><br/>
            <span>Version: {version}</span><br/>
        </div>
    );
}

export default ServerTemplateCard;
