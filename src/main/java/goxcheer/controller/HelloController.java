package goxcheer.controller;

import framework.annotation.MyAutowired;
import framework.annotation.MyController;
import framework.annotation.MyRequestMapping;
import framework.annotation.MyRequestParam;
import goxcheer.service.HelloService;

@MyController
@MyRequestMapping("/goxcheer")
public class HelloController {

    @MyAutowired
    private HelloService helloService;

    @MyRequestMapping("/say.do")
    public String Hello(@MyRequestParam(value = "username") String username,@MyRequestParam(value = "password") String password){
        return helloService.hello(username,password);
    }

}
