import styles from "./BurgerItem.module.css"
import * as React from "react";
import {useLocation, useNavigate} from "react-router";

export type BurgerItemProps = {
    children :
        | React.ReactElement
        | React.ReactElement[];
    route : string;
}

function BurgerItem({ children, route } : BurgerItemProps) {
    const navigate = useNavigate();
    const location = useLocation();
    function goto(){
        navigate({pathname: route, search: location.search})
    }
    return (
        <>
            <div className={`${styles.item} ${location.pathname === route ? styles.selected : ""}`} onClick={goto}>
                {children}
            </div>
        </>
    )
}

export default BurgerItem;
