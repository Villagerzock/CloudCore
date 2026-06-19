import type {Server} from "../lib/api.ts";
import styles from "./ServerCard.module.css";
import {useLocation, useNavigate} from "react-router";


function ServerCard({ id, name, template, online, max }: Server){
    const navigator = useNavigate();
    const location = useLocation();
    function navigate(){
        navigator({pathname: `/server/${id}`, search: location.search})
    }
    return (
        <div className={styles.card} onClick={navigate}>
            <h3>{name}</h3>
            <span>Template: {template}</span><br/>
            <span>Online: {online}/{max}</span><br/>
        </div>
    )
}

export default ServerCard;
