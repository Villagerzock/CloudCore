import type {JSX} from "react";

import styles from "./BurgerMenu.module.css"
import type BurgerItem from "./BurgerItem.tsx";
import type { BurgerItemProps } from "./BurgerItem.tsx";

type  BurgerMenuProps = {
    is_open : boolean;
    children:
        | React.ReactElement<BurgerItemProps, typeof BurgerItem>
        | React.ReactElement<BurgerItemProps, typeof BurgerItem>[];
}

function BurgerMenu({ is_open, children } : BurgerMenuProps) : JSX.Element {
    return (
        <div className={`${(is_open ? styles.open : styles.close)} ${styles.base}`}>
            {children}
        </div>
    )
}

export default BurgerMenu;