import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import {createBrowserRouter, RouterProvider} from "react-router";
import DashboardRoute from "./routes/DashboardRoute.tsx";
import ErrorRoute from "./routes/ErrorRoute.tsx";

function createRouter(){
    return createBrowserRouter([
        {
            path:"/",
            element: <App/>,
            children: [
                {
                    index: true,
                    element: <DashboardRoute/>
                },
                {
                    path:"*",
                    errorElement:<ErrorRoute/>,
                    element: <ErrorRoute/>
                }
            ]
        }
    ])
}

const router = createRouter();


createRoot(document.getElementById('root')!).render(
    <RouterProvider router={router}/>
)
