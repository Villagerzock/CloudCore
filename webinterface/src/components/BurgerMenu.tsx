import type {JSX} from "react";
import type {ReactNode} from "react";

import styles from "./BurgerMenu.module.css"

type  BurgerMenuProps = {
    is_open : boolean;
    children: ReactNode;
}

function BurgerMenu({ is_open, children } : BurgerMenuProps) : JSX.Element {
    return (
        <div className={`${(is_open ? styles.open : styles.close)} ${styles.base}`}>
            {children}
        </div>
    )
}

export default BurgerMenu;
