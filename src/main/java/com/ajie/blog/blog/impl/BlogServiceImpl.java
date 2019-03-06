package com.ajie.blog.blog.impl;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ajie.blog.blog.BlogService;
import com.ajie.blog.blog.RedisBlog;
import com.ajie.blog.blog.RedisBlogVo;
import com.ajie.blog.blog.exception.BlogException;
import com.ajie.blog.comment.CommentService;
import com.ajie.chilli.cache.redis.RedisClient;
import com.ajie.chilli.cache.redis.RedisException;
import com.ajie.chilli.common.MarkSupport;
import com.ajie.chilli.common.MarkVo;
import com.ajie.chilli.support.TimingTask;
import com.ajie.chilli.support.Worker;
import com.ajie.chilli.utils.TimeUtil;
import com.ajie.chilli.utils.common.JsonUtils;
import com.ajie.dao.mapper.TbBlogMapper;
import com.ajie.dao.pojo.TbBlog;
import com.ajie.dao.pojo.TbBlogExample;
import com.ajie.dao.pojo.TbBlogExample.Criteria;
import com.ajie.dao.pojo.TbUser;
import com.ajie.sso.role.Role;
import com.ajie.sso.role.RoleUtils;

@Service
public class BlogServiceImpl implements BlogService, MarkSupport, Worker {
	private static final Logger logger = LoggerFactory.getLogger(BlogServiceImpl.class);
	/** 数据库mapper */
	@Resource
	private TbBlogMapper mapper;

	/** 评论服务 */
	@Resource
	private CommentService commentService;

	/** redis缓存服务 */
	@Resource
	private RedisClient redisClient;

	public BlogServiceImpl() {
		// 定时对redis缓存数据进行处理
		String ymd = TimeUtil.formatYMD(new Date());
		new TimingTask(this, TimeUtil.parse(ymd + " 00:10"), 24 * 60 * 60 * 1000);// 零时十分，准时更新,每天这个时间执行
	}

	@Override
	public TbBlog getBlogById(int id, TbUser operator) throws BlogException {
		TbBlog blog = mapper.selectByPrimaryKey(id);
		if (null == blog)
			return null;
		// 从缓存数据中更新实时性不严格的字段
		assign(blog);
		if (RoleUtils.isAdmin(operator))
			return blog;// 管理员还能看到所有状态的博客，包括删除了的
		// 进行状态判断过滤
		int mark = blog.getMark();
		MarkVo markVo = getMarkVo(mark);
		if (markVo.isMark(MARK_STATE_DELETE))// 删除了，防止直接使用链接带上id访问
			throw new BlogException("找不到文章");
		if (markVo.isMark(VISIT_SELF.getId())
				&& (operator == null || operator.getId() != blog.getUserid()))
			throw new BlogException("找不到文章");
		return blog;
	}

	@Deprecated
	@Override
	public List<TbBlog> getBlogs(TbUser user) {
		if (null == user)
			return emptyList();
		if (user.getId() == 0) // 传过来的user忘带id，不能查询
			return emptyList();
		// 排除删除和仅自己可见的状态
		MarkVo mark = getMarkVo(0);
		mark.setMark(MARK_STATE_DELETE);
		int mark1 = mark.getMark();
		mark.setMark(VISIT_SELF.getId());
		int mark2 = mark.getMark();

		TbBlogExample ex = new TbBlogExample();
		Criteria criteria = ex.createCriteria();
		criteria.andUseridEqualTo(user.getId());
		criteria.andMarkNotEqualTo(mark1);
		criteria.andMarkNotEqualTo(mark2);
		List<TbBlog> blogs = mapper.selectByExample(ex);
		if (null == blogs)
			return emptyList();
		// 获取缓存的数据并赋值
		assign(blogs);
		return blogs;
	}

	@Deprecated
	@Override
	public List<TbBlog> getMyBlogs(TbUser loginer) {
		if (null == loginer)
			return emptyList();
		if (loginer.getId() == 0)
			return emptyList();
		MarkVo mark = getMarkVo(0);
		mark.setMark(MARK_STATE_DELETE);
		TbBlogExample ex = new TbBlogExample();
		Criteria criteria = ex.createCriteria();
		criteria.andUseridEqualTo(loginer.getId());
		criteria.andMarkNotEqualTo(mark.getMark());
		List<TbBlog> blogs = mapper.selectByExample(ex);
		if (null == blogs)
			return emptyList();
		// 获取缓存的数据并赋值
		assign(blogs);
		return blogs;
	}

	@Deprecated
	@Override
	public List<TbBlog> getBlogs(int state, TbUser operator) {
		// 非管理员或超级用户不能查看删除的博客
		/*if (null == operator && state == MARK_STATE_DELETE)

			if (null != operator
					&& state == MARK_STATE_DELETE
					&& (checkRole(operator.getRoleids(), "管理员") || checkRole(operator.getRoleids(),
							"超级用户"))) {
				return emptyList();
			}
		TbBlogExample ex = new TbBlogExample();
		Criteria criteria = ex.createCriteria();
		criteria.andMarkEqualTo(state);
		List<TbBlog> blogs = mapper.selectByExample(ex);
		if (null == blogs)
			return emptyList();
		return blogs;*/
		return emptyList();
	}

	@Override
	public List<TbBlog> getBlogs(TbUser user, int state, TbUser operator) {
		if (user != null && user.getId() == 0)
			return emptyList();
		// 不是管理员或su用户不能查看删除的博客
		if (state == MARK_STATE_DELETE && !isAdmin(operator)) {
			return emptyList();
		}
		// 不是自己或管理员操作，则不能获取私有的博客
		/*if (state == VISIT_SELF.getId()
				&& ((null != user && operator.getId() != user.getId()) || !isAdmin(operator))) {
			return emptyList();
		}*/
		if (state == VISIT_SELF.getId()) {
			if (null == operator) // 没有操作者即没有登录， 不能查看任何私有状态博客
				return emptyList();
			if (null == user && !isAdmin(operator)) // 不是管理员不能查看所有人的私有状态的博客
				return emptyList();
			if (user.getId() != operator.getId()) // 不是自己查看自己
				return emptyList();
		}
		TbBlogExample ex = new TbBlogExample();
		Criteria criteria = ex.createCriteria();
		if (null != user) {
			criteria.andUseridEqualTo(user.getId());
		}
		if (state > 0) { // 指定状态
			criteria.andMarkEqualTo(state);
		} else if (state == 0) { // 正常状态 排除删除和自己可见（如果不是自己操作）
			criteria.andMarkNotEqualTo(MARK_STATE_DELETE);
			if (null == user || operator.getId() != user.getId()) {
				criteria.andMarkNotEqualTo(VISIT_SELF.getId());
			}
		}
		List<TbBlog> blogs = mapper.selectByExample(ex);
		if (null == blogs)
			return emptyList();
		Collections.sort(blogs, CREATE_DATE);
		// 获取缓存的数据并赋值
		assign(blogs);
		return blogs;
	}

	@Override
	public List<TbBlog> searchBlogs(String keyword) {
		MarkVo mark = getMarkVo(0);
		mark.setMark(MARK_STATE_DELETE);
		int mark1 = mark.getMark();
		mark.setMark(MARK_VISIT_SELF);
		int mark2 = mark.getMark();
		// select * from tb_blog where (content like
		// "%keyword% and mark != mark1 & mark != mark2) or(title like "%keyword%
		// and mark != mark1 & mark != mark2)
		TbBlogExample ex = new TbBlogExample();
		Criteria criteria = ex.createCriteria();
		criteria.andContentLike(keyword);
		criteria.andMarkNotEqualTo(mark1);
		criteria.andMarkNotEqualTo(mark2);

		Criteria criteria2 = ex.createCriteria();
		criteria2.andTitleLike(keyword);
		criteria2.andMarkNotEqualTo(mark1);
		criteria2.andMarkNotEqualTo(mark2);
		ex.or(criteria2);
		List<TbBlog> blogs = mapper.selectByExample(ex);
		if (null == blogs)
			return emptyList();
		// 获取缓存的数据并赋值
		assign(blogs);
		return blogs;
	}

	@Override
	public List<TbBlog> searchTitle(String keyword) {
		TbBlogExample ex = new TbBlogExample();
		Criteria criteria = ex.createCriteria();
		criteria.andTitleLike(keyword);
		criteria.andMarkNotEqualTo(MARK_STATE_DELETE);
		criteria.andMarkNotEqualTo(MARK_VISIT_SELF);
		List<TbBlog> blogs = mapper.selectByExample(ex);
		if (null == blogs)
			return emptyList();
		// 获取缓存的数据并赋值
		assign(blogs);
		return blogs;
	}

	@Override
	public List<TbBlog> getHotBlog() {
		MarkVo mark = getMarkVo(MARK_STATE_HOT);
		mark.setMark(MARK_STATE_TOP);
		int markTop = mark.getMark();// 包含热门和置顶
		TbBlogExample ex = new TbBlogExample();
		Criteria criteria = ex.createCriteria();
		criteria.andMarkEqualTo(MARK_STATE_HOT);

		Criteria criteria2 = ex.createCriteria();
		criteria2.andMarkEqualTo(markTop);
		ex.or(criteria2);
		List<TbBlog> blogs = mapper.selectByExample(ex);
		if (null == blogs)
			return emptyList();
		// 获取缓存的数据并赋值
		assign(blogs);
		return blogs;
	}

	@Override
	public List<TbBlog> getTop(TbUser user) {
		if (null == user)
			return emptyList();
		if (user.getId() == 0)
			return emptyList();
		TbBlogExample ex = new TbBlogExample();
		Criteria criteria = ex.createCriteria();
		criteria.andUseridEqualTo(user.getId());
		criteria.andMarkEqualTo(STATE_HOT.getId());
		List<TbBlog> blogs = mapper.selectByExample(ex);
		if (null == blogs)
			return emptyList();
		// 获取缓存的数据并赋值
		assign(blogs);
		return blogs;
	}

	@Override
	public TbBlog createBlog(TbBlog blog) throws BlogException {
		if (null == blog)
			return null;
		int ret = mapper.insert(blog);
		if (ret != 1)
			throw new BlogException("博客发布失败，请稍后再试");
		return blog;
	}

	@Override
	public void deleteBlog(TbBlog blog, TbUser operator) throws BlogException {
		if (null == blog || blog.getId() == 0)
			throw new BlogException("删除异常，请稍后再试");
		if (null == operator)
			throw new BlogException("删除失败，操作用户为空");
		/*// 防数据被篡改
		// 上面的判断通过了仍不能说明blog的拥有者就是operator，如果在传输过程中改变了blog的id，但是userid没有改变，就会出现删除别的博客
		blog = mapper.selectByPrimaryKey(blog.getId());
		if (blog.getUserid() != operator.getId())
			throw new BlogException("删除失败，不能删除非自己的博客");
		blog.setMark(MARK_STATE_DELETE);
		mapper.updateByPrimaryKey(blog);*/
		mapper.updateBlogMark(blog.getId(), operator.getId(), MARK_STATE_DELETE);
		logger.info("用户" + operator.getId() + " 删除了博客:" + blog.getId());
	}

	@Override
	public void deleteAll(TbUser user, TbUser operator) throws BlogException {
		if (null == user || user.getId() == 0 || null == operator || operator.getId() == 0)
			throw new BlogException("删除异常，请稍后再试");
		if (user.getId() != operator.getId() || !isAdmin(operator))
			throw new BlogException("删除失败，不能删除非自己的博客");
		/*TbBlogExample ex = new TbBlogExample();
		Criteria criteria = ex.createCriteria();
		criteria.andUseridEqualTo(user.getId());
		List<TbBlog> blogs = mapper.selectByExample(ex);
		for (TbBlog blog : blogs) {
			if (blog.getUserid() != operator.getId()) {
				logger.error("批量删除过程中，发现有博客拥有者和操作者不匹配，跳过该操作,blog.user" + blog.getUserid()
						+ "operator:" + operator.getId());
				continue;
			}
			blog.setMark(MARK_STATE_DELETE);
			mapper.updateByPrimaryKey(blog);
		}*/
		mapper.updateBlogsMark(operator.getId(), MARK_STATE_DELETE);
		logger.info("用户" + operator.getId() + " 删除了所有的博客");
	}

	@Override
	public TbBlog updateBlog(TbBlog blog) throws BlogException {
		mapper.updateByPrimaryKey(blog);
		return blog;
	}

	@Override
	public MarkVo getMarkVo(int mark) {
		return new MarkVo(mark);
	}

	private List<TbBlog> emptyList() {
		return Collections.emptyList();
	}

	/**
	 * 获取redis缓存的数据
	 * 
	 * @param blog
	 */
	private void assign(TbBlog blog) {
		RedisBlog vo = new RedisBlog(redisClient, blog.getId());
		vo.assign(blog);
	}

	/**
	 * 获取redis缓存的数据
	 * 
	 * @param blog
	 */
	private void assign(List<TbBlog> blogs) {
		for (TbBlog blog : blogs) {
			RedisBlog vo = new RedisBlog(redisClient, blog.getId());
			vo.assign(blog);
		}
	}

	/**
	 * 检查权限id集里是否有roleName名字的权限
	 * 
	 * @param roleids
	 * @param roleName
	 * @return
	 */
	private boolean checkRole(String roleids, String roleName) {
		List<Role> roles = JsonUtils.toList(roleids, Role.class);
		if (null == roles)
			return false;
		for (Role role : roles) {
			if (role.getName().equals(roleName)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 检查用户是否为管理员用户
	 * 
	 * @param user
	 * @return
	 */
	private boolean isAdmin(TbUser user) {
		return checkRole(user.getRoleids(), "管理员") || checkRole(user.getRoleids(), "超级用户");
	}

	@Override
	public List<TbBlog> getBlogByIds(List<Integer> ids){
		if (null == ids || ids.isEmpty())
			return emptyList();
		TbBlogExample ex = new TbBlogExample();
		for (int id : ids) {
			Criteria criteria = ex.createCriteria();
			criteria.andIdEqualTo(id);
			ex.or(criteria);
		}
		List<TbBlog> blogs = mapper.selectByExample(ex);
		if (null == blogs)
			return emptyList();
		return blogs;
	}

	@Override
	public void work() {
		System.out.println("定时任务");
		TbBlogExample ex = new TbBlogExample();
		// 更新评论数和阅读数
		List<TbBlog> blogs = mapper.selectByExample(ex);
		for (TbBlog blog : blogs) {
			int commentCount = commentService.getBlogCommentCount(blog.getId());
			int readNum = 0;
			try {
				RedisBlogVo vo = redisClient.hgetAsBean(REDIS_PREFIX, REDIS_PREFIX + blog.getId(),
						RedisBlogVo.class);
				if (null != vo)
					readNum = vo.getReadnum();
			} catch (RedisException e) {
				logger.warn("无法从redis中获取blog", e);
			}
			mapper.updateBlogCRCount(blog.getId(), commentCount, readNum);
		}
		logger.info("更新blog的评论数和阅读数");
	}
}
