package goxcheer.service;

import framework.annotation.MyService;

@MyService
public class HelloServiceImpl implements HelloService {

    @Override
    public String hello(String username,String password) {
        return username+":hello,"+password;
    }
}
