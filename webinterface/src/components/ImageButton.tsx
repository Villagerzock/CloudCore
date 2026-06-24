import  {type MouseEventHandler} from "react";
import styles from "./ImageButton.module.css"
import * as React from "react";


function ImageButton({ onClick, children, tooltip }: { onClick: MouseEventHandler<HTMLButtonElement>, children : React.ReactElement, tooltip? : string }) {
    return (
        <button type="button" onClick={onClick} className={styles.button} title={tooltip}>
            {children}
        </button>
    )
}

export default ImageButton;
