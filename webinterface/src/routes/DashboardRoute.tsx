import styles from "./DashboardRoute.module.css"
import {CartesianGrid, Line, LineChart, Tooltip, XAxis, YAxis} from "recharts";


const test_data = [
    { date: "01.06.2026", value: 153 },
    { date: "02.06.2026", value: 213 },
    { date: "03.06.2026", value: 198 },
    { date: "04.06.2026", value: 225 },
    { date: "05.06.2026", value: 241 },
    { date: "06.06.2026", value: 234 },
    { date: "07.06.2026", value: 256 },
    { date: "08.06.2026", value: 249 },
    { date: "09.06.2026", value: 271 },
    { date: "10.06.2026", value: 265 },
    { date: "11.06.2026", value: 288 },
    { date: "12.06.2026", value: 302 },
    { date: "13.06.2026", value: 295 },
    { date: "14.06.2026", value: 317 },
    { date: "15.06.2026", value: 326 },
    { date: "16.06.2026", value: 321 },
    { date: "17.06.2026", value: 348 },
    { date: "18.06.2026", value: 362 },
    { date: "19.06.2026", value: 354 },
    { date: "20.06.2026", value: 381 },
    { date: "21.06.2026", value: 397 },
    { date: "22.06.2026", value: 389 },
    { date: "23.06.2026", value: 425 },
    { date: "24.06.2026", value: 447 },
    { date: "25.06.2026", value: 438 },
    { date: "26.06.2026", value: 481 },
    { date: "27.06.2026", value: 516 },
    { date: "28.06.2026", value: 503 },
    { date: "29.06.2026", value: 578 },
    { date: "30.06.2026", value: 642 }
];

function DashboardRoute(){
    return (
        <>
            <h1>Dashboard</h1>
            <div className={styles.charts}>
                <LineChart width={500} height={300} data={test_data}>
                    <CartesianGrid strokeDasharray={"3 3"}/>
                    <XAxis dataKey={"date"}/>
                    <YAxis/>
                    <Tooltip/>
                    <Line type={"monotone"} dataKey={"value"}/>
                </LineChart>
            </div>
        </>
    );
}
export default DashboardRoute;