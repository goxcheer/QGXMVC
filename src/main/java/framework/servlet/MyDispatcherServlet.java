package framework.servlet;

import framework.annotation.MyController;
import framework.annotation.MyRequestMapping;
import framework.annotation.MyRequestParam;
import framework.context.MyApplicationContext;
import jdk.internal.org.objectweb.asm.Handle;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyDispatcherServlet extends HttpServlet {

    private static final String LOCATION = "contextConfiguration";

    private List<Handler> handlerMapping = new ArrayList<Handler>();

    private Map<Handler, HandlerAdapter> adapterMapping = new HashMap<Handler, HandlerAdapter>();

    private List<ViewResolver> viewResolvers = new ArrayList<ViewResolver>();


    @Override
    public void init(ServletConfig config) throws ServletException {
        //ico容器的初始化
        MyApplicationContext myApplicationContext = new MyApplicationContext(config.getInitParameter(LOCATION));
        //请求解析
        initMultipartResolver(myApplicationContext);
        //多语言、国际化
        initLocaleResolver(myApplicationContext);
        //主题View层的
        initThemeResolver(myApplicationContext);
        //============== 重要 ================
        //解析url和Method的关联关系
        initHandlerMapping(myApplicationContext);
        //适配器（匹配的过程）
        initHandlerAdapters(myApplicationContext);
        //============== 重要 ================
        //异常解析
        initHandlerExceptionResolvers(myApplicationContext);
        //视图转发（根据视图名字匹配到一个具体模板）
        initRequestToViewNameTranslator(myApplicationContext);

        //解析模板中的内容（拿到服务器传过来的数据，生成HTML代码）
        initViewResolvers(myApplicationContext);

        initFlashMapManager(myApplicationContext);

        System.out.println("My MVC is init.");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doDispatch(req, resp);
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) {
        try {
            //从HandlerMapping中取Handler
            Handler handler = getHandler(req);
            if (handler == null) {
                resp.getWriter().write("404 NOT FOUND");
                return;
            }
            HandlerAdapter handlerAdapter = getHandlerAdapter(handler);
            Object o = handlerAdapter.handle(req, resp, handler);
            if (o instanceof MyModelAndView){
                applyDefaultViewName(resp, (MyModelAndView) o);
            }else {
                resp.getWriter().write(o.toString());
            }


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void applyDefaultViewName(HttpServletResponse resp, MyModelAndView myModelAndView) throws IOException {
        if(null == myModelAndView){ return;}
        if(viewResolvers.isEmpty()){ return;}

        for (ViewResolver resolver : viewResolvers) {
          /*  if(!myModelAndView.getView().equals(resolver.getViewName())){ continue; }

            String r = resolver.parse(mv);*/
            String r = null;
            if(r != null){
                resp.getWriter().write(r);
                break;
            }
        }
    }

    private HandlerAdapter getHandlerAdapter(Handler handler) {
        if (adapterMapping.isEmpty()) {
            return null;
        }
        return adapterMapping.get(handler);
    }

    private Handler getHandler(HttpServletRequest req) {
        //循环handlerMapping
        if (handlerMapping.isEmpty()) {
            return null;
        }

        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath, "").replaceAll("/+", "/");

        for (Handler handler : handlerMapping) {
            Matcher matcher = handler.pattern.matcher(url);
            if (!matcher.matches()) {
                continue;
            }
            return handler;
        }
        return null;
    }


    private void initFlashMapManager(MyApplicationContext myApplicationContext) {
    }

    private void initViewResolvers(MyApplicationContext myApplicationContext) {
    }

    private void initRequestToViewNameTranslator(MyApplicationContext myApplicationContext) {
    }

    private void initHandlerExceptionResolvers(MyApplicationContext myApplicationContext) {
    }

    /**
     * 动态匹配参数的适配器
     *
     * @param myApplicationContext
     */
    private void initHandlerAdapters(MyApplicationContext myApplicationContext) {
        if (handlerMapping.isEmpty()) {
            return;
        }
        //参数类型作为key，参数的索引号作为值
        Map<String, Integer> paramMapping = new HashMap<String, Integer>();

        for (Handler handler : handlerMapping) {
            Class<?>[] parameterTypes = handler.method.getParameterTypes();
            //有顺序，但是通过反射，没法拿到我们参数名字
            //匹配自定参数列表
            for (int i = 0; i < parameterTypes.length; i++) {
                Class<?> type = parameterTypes[i];
                if (type == HttpServletRequest.class || type == HttpServletResponse.class) {
                    paramMapping.put(type.getName(), i);
                }
            }
            //这里是匹配Request和Response
            Annotation[][] pa = handler.method.getParameterAnnotations();
            for (int i = 0; i < pa.length; i++) {
                for (Annotation a : pa[i]) {
                    if (a instanceof MyRequestParam) {
                        String paramName = ((MyRequestParam) a).value();
                        if (!"".equals(paramName.trim())) {
                            paramMapping.put(paramName, i);
                        }

                    }
                }
            }
            adapterMapping.put(handler, new HandlerAdapter(paramMapping));
        }

    }

    private void initHandlerMapping(MyApplicationContext myApplicationContext) {
        Map<String, Object> iocMap = myApplicationContext.getIocMap();
        if (iocMap.isEmpty()) {
            return;
        }
        //只要是由Cotroller修饰类，里面方法全部找出来
        //而且这个方法上应该要加了RequestMaping注解，如果没加这个注解，这个方法是不能被外界来访问的
        for (Map.Entry<String, Object> entry : iocMap.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();

            if (!clazz.isAnnotationPresent(MyController.class)) { //不包含Controller
                continue;
            }

            String url = "";
            //类上有UrlMapping
            if (clazz.isAnnotationPresent(MyRequestMapping.class)) {
                MyRequestMapping myRequestMapping = clazz.getAnnotation(MyRequestMapping.class);
                url = myRequestMapping.value();
            }
            //Controller所有的方法
            Method[] methods = clazz.getMethods();
            for (Method method : methods) {
                if (!method.isAnnotationPresent(MyRequestMapping.class)) { //方法没有被注解修饰
                    continue;
                }
                MyRequestMapping myRequestMapping = method.getAnnotation(MyRequestMapping.class);
                String regex = (url + myRequestMapping.value()).replaceAll("/+", "/");

                Pattern pattern = Pattern.compile(regex);

                handlerMapping.add(new Handler(pattern, entry.getValue(), method));

                System.out.println("Mapping: " + regex + " " + method.toString());

            }

        }
    }

    private void initThemeResolver(MyApplicationContext myApplicationContext) {
    }

    private void initLocaleResolver(MyApplicationContext myApplicationContext) {
    }

    private void initMultipartResolver(MyApplicationContext myApplicationContext) {
    }

    private class HandlerAdapter {
        private Map<String, Integer> paramMapping;

        public HandlerAdapter(Map<String, Integer> paramMapping) {
            this.paramMapping = paramMapping;
        }

        //用反射调用url对应的method
        public Object handle(HttpServletRequest request, HttpServletResponse response, Handler handler) throws InvocationTargetException, IllegalAccessException {
            //获取方法的所有参数
            Class<?>[] parameterTypes = handler.method.getParameterTypes();
            //给参数赋值，通过索引号来完成
            Object[] paramValues = new Object[parameterTypes.length];
            Map<String, String[]> params = request.getParameterMap(); //请求的所有参数

            for (Map.Entry<String, String[]> param : params.entrySet()) {
                String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");

                if (!this.paramMapping.containsKey(param.getKey())) {
                    continue;
                }

                int index = this.paramMapping.get(param.getKey());

                paramValues[index] = castStringValue(value, parameterTypes[index]);

                //request 和 response 要赋值
                String reqName = HttpServletRequest.class.getName();
                if (this.paramMapping.containsKey(reqName)) {
                    int reqIndex = this.paramMapping.get(reqName);
                    paramValues[reqIndex] = request;
                }


                String resqName = HttpServletResponse.class.getName();
                if (this.paramMapping.containsKey(resqName)) {
                    int respIndex = this.paramMapping.get(resqName);
                    paramValues[respIndex] = response;
                }
            }
            boolean isModelAndView = handler.method.getReturnType() == MyModelAndView.class;
            Object o = handler.method.invoke(handler.controller, paramValues);
            return o;
        }

        private Object castStringValue(String value, Class<?> clazz) {
            if (clazz == String.class) {
                return value;
            } else if (clazz == Integer.class) {
                return Integer.valueOf(value);
            } else if (clazz == int.class) {
                return Integer.valueOf(value).intValue();
            } else {
                return null;
            }
        }
    }

    /**
     * Handler内部类
     */
    private class Handler {
        protected Object controller;
        protected Method method;
        protected Pattern pattern;

        protected Handler(Pattern pattern, Object controller, Method method) {
            this.pattern = pattern;
            this.controller = controller;
            this.method = method;
        }
    }

    /**
     * 视图解析器
     */
    private class ViewResolver {

    }
}
