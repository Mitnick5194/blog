<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<meta name="viewport" content="width=device-width , initial-scale=1">
<title>${empty id ? '发布博客': '编辑博客' }</title>
<link href="../css/common.css" rel="stylesheet" type="text/css">
<script type="text/javascript" src="/js/jquery-1.9.1.js"></script>
<style type="text/css">
.nav{width: 90%; margin: 0 auto; height: 40px;background: #eee;}
.main{width: 90%;margin: 0 auto;margin-top: 20px;}
.main>.left{width: 260px;}
.main>.editor-area{width: 100%;}
.form>input[name=title]{width: 98%;height: 40px;line-height: 40px;background: #eee;margin-bottom: 5px;padding: 0 10px;border: none;font-size: 16px;}
.labels{height: 30px;line-height: 30px;align-items: center;padding:10px 0;}
.add-label{position: relative;color: #349EDF;cursor: pointer;}
/* .add-label:before{content:'';position: absolute;width: 20px;height: 20px;border-radius: 3px;background:#349EDF;color:#fff;left: -25px;top: 5px;} */
.add-label>div{display: inline-block;}
.add-label-btn{position: absolute;left: 0;width: 20px;height: 20px;background:#349EDF;border-radius: 3px;top: 5px; }
.add-label-btn>span:nth-child(1){position: absolute;top:9px;height: 2px;background: #fff;width:16px;left: 2px;}
.add-label-btn>span:nth-child(2){position: absolute;width: 2px;background: #fff;left:9px;top: 2px;height:16px;}
.add-label-hits{margin-left: 25px;}
.input-group{display: flex;}
.input-group input{background: #eee;border: none;width: 80px;border-radius: 3px;padding: 5px; }
.delLabelBtn{display: inline-block;width: 20px;height: 100%;color: #888;font-size: 22px;margin-right: 3px;text-align: center;cursor: pointer;}
</style>
 <script src="/ckeditor/ckeditor.js"></script>
</head>
<body>
	
	<div >
		<div class="nav">这是 导航</div>
		<div class="flex main">
			<div class="left">left</div>
			<div class="editor-area">
				<form class="form" id="iForm">
					<input placeholder="文章标题" type="text" name="title"/>
			    	<textarea  name="editor" id="editor"></textarea>
			    	<div class="labels flex">
			    		<div>文章标签：</div>
			    		<div class="input-group" id="iInputGruop">
			    			<!-- <input type="text" /><span class="del-label-btn">x</span>
			    			<input type="text" />
			    			<input type="text" /> -->
			    		</div>
			    		<div class="add-label" id="iAddLabelBtn">
			    			<div class="add-label-btn"><span></span><span></span></div>
			    			<div class="add-label-hits">添加标签</div>
			    		</div>
			    	</div>
			     </form> 
			     <button id="iBtn">点击</button>
			</div>
		</div>
	</div>
     <script type="text/javascript" src="/blog/js/addblog.js"></script>
      <script>
          
	   </script>
</body>
</html>