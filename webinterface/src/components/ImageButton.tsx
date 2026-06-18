import  {type MouseEventHandler} from "react";
import styles from "./ImageButton.module.css"
import * as React from "react";


function ImageButton({ onClick, children }: { onClick: MouseEventHandler<HTMLButtonElement>, children : React.ReactElement }) {
    return (
        <button onClick={onClick} className={styles.button}>
            {children}
        </button>
    )
}

export default ImageButton;