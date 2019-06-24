package framework.context;

import framework.annotation.MyAutowired;
import framework.annotation.MyController;
import framework.annotation.MyService;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class MyApplicationContext {

    private Map<String,Object> iocMap = new ConcurrentHashMap<>();

    //类似于内部的配置信息，我们在外面是看不到的
    //我们能够看到的只有ioc容器  getBean方法来间接调用的
    private List<String> classCache = new ArrayList<String>();

    private Properties config = new Properties();

    public MyApplicationContext(String location) {
        //1.定位
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(location);
        //2.载入
        try {
            config.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //3.注册
        String basePackage = config.getProperty("scanPackage");
        doRegister(basePackage);
        //实例化
        doCreateBeans();
        //5.依赖注入
        doPopulate();
    }

    public Map<String,Object>getIocMap(){
        return this.iocMap;
    }

    /**
     * 依赖注入
     */
    private void doPopulate() {
        if (iocMap.isEmpty()){
            return;
        }
        for(Map.Entry<String,Object>entry: iocMap.entrySet()){
            //把所有的属性全部取出来，包括私有属性
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field: fields){
                if (!field.isAnnotationPresent(MyAutowired.class)){
                    continue;
                }
                MyAutowired myAutowired = field.getAnnotation(MyAutowired.class);
                String id = myAutowired.value().trim();
                //如果id为空，也就是说，自己没有设置，默认根据类型来注入
                if ("".equals(id)){
                    id = field.getType().getName();
                }
                field.setAccessible(true); //把私有变量的访问权限放开
                try {
                    field.set(entry.getValue(), iocMap.get(id));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    continue;
                }
            }
        }
    }

    /**
     * 实例化Bean
     */
    private void doCreateBeans() {
        //检查看有没有注册信息,注册信息里面保存了所有的class名字
        if (classCache.size() == 0){
            return;
        }
        for (String className: classCache){
            try {
                Class<?> clazz = Class.forName(className);
                //那个类需要初始化，哪个类不要初始化
                //只要加了  @Service  @Controller都要初始化
                if (clazz.isAnnotationPresent(MyController.class)){
                    String id = lowerFirstChar(clazz.getSimpleName());
                    iocMap.put(id,clazz.newInstance());
                }else if (clazz.isAnnotationPresent(MyService.class)){
                    MyService myService = clazz.getAnnotation(MyService.class);
                    //Service有别名，用别名，默认是类首字母小写
                    String id = myService.value();
                    if (! "".equals(id.trim())){
                       iocMap.put(id, clazz.newInstance());
                       continue;
                    }

                    Class<?>[] interfaces = clazz.getInterfaces();
                    for (Class<?> c:interfaces){
                        iocMap.put(c.getName(), clazz.newInstance());
                    }
                }
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }
    /**
     * 将包下的所有class保存到list
     * @param basePackage
     */
    private void doRegister(String basePackage) {
        URL url = this.getClass().getClassLoader().getResource("/" + basePackage.replaceAll("\\.", "/"));
        File dir = new File(url.getFile());
        for (File file: dir.listFiles()){
            if (file.isDirectory()){
                doRegister(basePackage + "." + file.getName()); //是文件夹则递归
            }else {
                classCache.add(basePackage + "." + file.getName().replace(".class","").trim());
            }
        }
    }

    /**
     * 字符串首字符小写
     * @param name
     * @return
     */
    private String lowerFirstChar(String name) {
        char [] chars = name.toCharArray();
        chars[0]+=32;
        return String.valueOf(chars);
    }

    public static void main(String[] args) {
        URL url = MyApplicationContext.class.getClassLoader().getResource("goxcheer");
    }
}
