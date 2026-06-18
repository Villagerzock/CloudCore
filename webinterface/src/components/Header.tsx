import {type JSX} from "react";
import styles from "./Header.module.css"
import ImageButton from "./ImageButton.tsx";
import {FaBars} from "react-icons/fa";

function Header({ burger_menu_state, open_burger_menu } : { burger_menu_state : boolean, open_burger_menu : (val : boolean) => void }) : JSX.Element {

    function toggle(){
        open_burger_menu(!burger_menu_state);
    }

    return (
        <>
            <div className={styles.header}>
                <div className={styles.left}>
                    <ImageButton onClick={toggle}><FaBars size={"2em"}/></ImageButton>
                </div>
                <div className={styles.right}></div>
            </div>
        </>
    );
}

export default Header;