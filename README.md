# openfly-recipe-builder

build rime recipe from official dict used on sogou

recipe based [openfly](https://github.com/amorphobia/openfly) but delete all lua scripts, only support basic input function

## how to use?

```shell
mv openfly-recipe-builder /path/to/your/rime/user/dict
mv /flypy/official/sougo/dict.txt /path/to/your/rime/user/dict 
chmod 775 openfly-recipe-builder
./openfly-recipe-builder
```