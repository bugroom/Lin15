# 这里是专门的解答区

# 0、为什么设置会无效？变透明

目前原因未知，尤其是只变透明这一现象，请发xposed的日志给我。

# 1、点击图标无效，没有任何反应

无反应的原因只有一个，那就是lin15没有真正获取到可以设置背景的class，systemUI被改了，或是被混淆了。导致在寻找的时候找不到了，如果是这样，说实话适配的难度非常大，个人不太想去适配。

# 2、图片剪切高度不对，比快速设置面板短

高度是依据设置面板来获取的，如果高度不一致，那应该是获取到的高度有问题，建议使用自定义高度功能将之前保存的高度清空（设置为0），之后再重新下拉整个设置面板到底，再进行设置

# 3、下拉有点卡啊

正常现象，毕竟这图也占了很大一部分内存。当然可以调教，多压制一下后台就行了。当然个人推荐用低质量图片，图片不要太风骚太花俏，毕竟图越复杂绘制时间越长。

# 4、我想提交日志文件给作者排查bug

请将日志文件以文件的形式发送到liuzhu234@gmail.com，如果点击无效，可以的话，还可以将systemUI发给我看看