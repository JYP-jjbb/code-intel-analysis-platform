import { createApp } from "vue";
import ElementPlus from "element-plus";
import "element-plus/dist/index.css";
import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import App from "./App.vue";
import router from "./router/index.js";
import "./styles.css";

gsap.registerPlugin(ScrollTrigger);

theApp();

function theApp() {
  const app = createApp(App);
  app.use(ElementPlus);
  app.use(router);
  app.mount("#app");
}
