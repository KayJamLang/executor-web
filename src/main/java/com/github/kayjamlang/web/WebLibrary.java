package com.github.kayjamlang.web;

import com.github.kayjamlang.core.Argument;
import com.github.kayjamlang.core.Type;
import com.github.kayjamlang.executor.Context;
import com.github.kayjamlang.executor.libs.Library;
import com.github.kayjamlang.executor.libs.main.MapClass;

import java.util.HashMap;
import java.util.Map;

public class WebLibrary extends Library {

    public WebLibrary(){
        functions.add(new LibFunction("header", (mainContext, context)->{
            String name = context.variables.get("name").toString();
            String value = context.variables.get("value").toString();
            HeaderPut headerPut = (HeaderPut) mainContext.parent.data.get("headerPut");
            headerPut.put(name, value);
            return true;
        }, new Argument(Type.ANY, "name"),
                new Argument(Type.ANY, "value")));

        functions.add(new LibFunction("getRequest", (mainContext, context)->
                new LibObject(object -> {
                    object.addVariable("method",
                            mainContext.parent.data.get("requestMethod"));
                    object.addVariable("postData", mainContext.parent.data.get("post"));

                    try {
                        MapClass mapClass = new MapClass();
                        Context ctx = new Context(mapClass, mainContext, false);
                        ctx.variables.put(MapClass.FIELD_MAP, mainContext.parent.data.get("query"));

                        mapClass.data.put("ctx", ctx);
                        object.addVariable("query", mapClass);
                    } catch (Exception ignored) {}
        })));
    }

    public interface HeaderPut{
        void put(String name, String value);
    }
}
