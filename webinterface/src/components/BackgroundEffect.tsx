import {useEffect} from "react";

function BackgroundEffect(){
    useEffect(() => {
        const onMouseMove = (e: MouseEvent) => {
            const x = (e.clientX / window.innerWidth - 0.5) * -10;
            const y = (e.clientY / window.innerHeight - 0.5) * -10;

            document.documentElement.style.setProperty("--light-x", `${x}%`);
            document.documentElement.style.setProperty("--light-y", `${y}%`);
        };

        window.addEventListener("mousemove", onMouseMove);
        return () => window.removeEventListener("mousemove", onMouseMove);
    }, []);

    return <></>;
}

export default BackgroundEffect;