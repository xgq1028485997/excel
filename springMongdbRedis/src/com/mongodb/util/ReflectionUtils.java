package com.mongodb.util;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class ReflectionUtils {
	private static Logger logger = LoggerFactory.getLogger(ReflectionUtils.class);

	/**
	 * ����Getter����.
	 */
	public static Object invokeGetterMethod(Object obj, String propertyName) {
		String getterMethodName = "get" + StringUtils.capitalize(propertyName);
		return invokeMethod(obj, getterMethodName, new Class[] {}, new Object[] {});
	}

	/**
	 * ����Setter����.ʹ��value��Class������Setter����.
	 */
	public static void invokeSetterMethod(Object obj, String propertyName, Object value) {
		invokeSetterMethod(obj, propertyName, value, null);
	}

	/**
	 * ����Setter����.
	 * 
	 * @param propertyType
	 *            ���ڲ���Setter����,Ϊ��ʱʹ��value��Class���.
	 */
	public static void invokeSetterMethod(Object obj, String propertyName, Object value, Class<?> propertyType) {
		Class<?> type = propertyType != null ? propertyType : value.getClass();
		String setterMethodName = "set" + StringUtils.capitalize(propertyName);
		invokeMethod(obj, setterMethodName, new Class[] { type }, new Object[] { value });
	}

	/**
	 * ֱ�Ӷ�ȡ��������ֵ, ����private/protected���η�, ������getter����.
	 */
	public static Object getFieldValue(final Object obj, final String fieldName) {
		Field field = getAccessibleField(obj, fieldName);

		if (field == null) {
			throw new IllegalArgumentException("Could not find field [" + fieldName + "] on target [" + obj + "]");
		}

		Object result = null;
		try {
			result = field.get(obj);
		} catch (IllegalAccessException e) {
			logger.error("�������׳����쳣{}", e.getMessage());
		}
		return result;
	}

	/**
	 * ֱ�����ö�������ֵ, ����private/protected���η�, ������setter����.
	 */
	public static void setFieldValue(final Object obj, final String fieldName, final Object value) {
		Field field = getAccessibleField(obj, fieldName);

		if (field == null) {
			throw new IllegalArgumentException("Could not find field [" + fieldName + "] on target [" + obj + "]");
		}

		try {
			field.set(obj, value);
		} catch (IllegalAccessException e) {
			logger.error("�������׳����쳣:{}", e.getMessage());
		}
	}

	/**
	 * ѭ������ת��, ��ȡ�����DeclaredField, ��ǿ������Ϊ�ɷ���.
	 * 
	 * ������ת�͵�Object���޷��ҵ�, ����null.
	 */
	public static Field getAccessibleField(final Object obj, final String fieldName) {
		Assert.notNull(obj, "object����Ϊ��");
		Assert.hasText(fieldName, "fieldName");
		for (Class<?> superClass = obj.getClass(); superClass != Object.class; superClass = superClass
				.getSuperclass()) {
			try {
				Field field = superClass.getDeclaredField(fieldName);
				field.setAccessible(true);
				return field;
			} catch (NoSuchFieldException e) {// NOSONAR
				// Field���ڵ�ǰ�ඨ��,��������ת��
			}
		}
		return null;
	}

	/**
	 * ֱ�ӵ��ö��󷽷�, ����private/protected���η�. ����һ���Ե��õ����.
	 */
	public static Object invokeMethod(final Object obj, final String methodName, final Class<?>[] parameterTypes,
			final Object[] args) {
		Method method = getAccessibleMethod(obj, methodName, parameterTypes);
		if (method == null) {
			throw new IllegalArgumentException("Could not find method [" + methodName + "] on target [" + obj + "]");
		}

		try {
			return method.invoke(obj, args);
		} catch (Exception e) {
			throw convertReflectionExceptionToUnchecked(e);
		}
	}

	/**
	 * ѭ������ת��, ��ȡ�����DeclaredMethod,��ǿ������Ϊ�ɷ���. ������ת�͵�Object���޷��ҵ�, ����null.
	 * 
	 * ���ڷ�����Ҫ����ε��õ����. ��ʹ�ñ�������ȡ��Method,Ȼ�����Method.invoke(Object obj, Object...
	 * args)
	 */
	public static Method getAccessibleMethod(final Object obj, final String methodName,
			final Class<?>... parameterTypes) {
		Assert.notNull(obj, "object����Ϊ��");

		for (Class<?> superClass = obj.getClass(); superClass != Object.class; superClass = superClass
				.getSuperclass()) {
			try {
				Method method = superClass.getDeclaredMethod(methodName, parameterTypes);

				method.setAccessible(true);

				return method;

			} catch (NoSuchMethodException e) {// NOSONAR
				// Method���ڵ�ǰ�ඨ��,��������ת��
			}
		}
		return null;
	}

	/**
	 * ͨ������, ���Class�����������ĸ���ķ��Ͳ���������. ���޷��ҵ�, ����Object.class. eg. public UserDao
	 * extends HibernateDao<User>
	 * 
	 * @param clazz
	 *            The class to introspect
	 * @return the first generic declaration, or Object.class if cannot be
	 *         determined
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <T> Class<T> getSuperClassGenricType(final Class clazz) {
		return getSuperClassGenricType(clazz, 0);
	}

	/**
	 * ͨ������, ���Class�����������ĸ���ķ��Ͳ���������. ���޷��ҵ�, ����Object.class.
	 * 
	 * ��public UserDao extends HibernateDao<User,Long>
	 * 
	 * @param clazz
	 *            clazz The class to introspect
	 * @param index
	 *            the Index of the generic ddeclaration,start from 0.
	 * @return the index generic declaration, or Object.class if cannot be
	 *         determined
	 */
	@SuppressWarnings("rawtypes")
	public static Class getSuperClassGenricType(final Class clazz, final int index) {

		Type genType = clazz.getGenericSuperclass();

		if (!(genType instanceof ParameterizedType)) {
			logger.warn(clazz.getSimpleName() + "'s superclass not ParameterizedType");
			return Object.class;
		}

		Type[] params = ((ParameterizedType) genType).getActualTypeArguments();

		if (index >= params.length || index < 0) {
			logger.warn("Index: " + index + ", Size of " + clazz.getSimpleName() + "'s Parameterized Type: "
					+ params.length);
			return Object.class;
		}
		if (!(params[index] instanceof Class)) {
			logger.warn(clazz.getSimpleName() + " not set the actual class on superclass generic parameter");
			return Object.class;
		}

		return (Class) params[index];
	}

	/**
	 * ������ʱ��checked exceptionת��Ϊunchecked exception.
	 */
	public static RuntimeException convertReflectionExceptionToUnchecked(Exception e) {
		if (e instanceof IllegalAccessException || e instanceof IllegalArgumentException
				|| e instanceof NoSuchMethodException) {
			return new IllegalArgumentException("Reflection Exception.", e);
		} else if (e instanceof InvocationTargetException) {
			return new RuntimeException("Reflection Exception.", ((InvocationTargetException) e).getTargetException());
		} else if (e instanceof RuntimeException) {
			return (RuntimeException) e;
		}
		return new RuntimeException("Unexpected Checked Exception.", e);
	}

	/**
	 * ���ݶ�����mongodb Update��� ��id�ֶ����⣬���б���ֵ���ֶζ����Ϊ�޸���
	 */
	public static Update getUpdateObj(final Object obj) {
		if (obj == null)
			return null;
		Field[] fields = obj.getClass().getDeclaredFields();
		Update update = null;
		boolean isFirst = true;
		for (Field field : fields) {
			field.setAccessible(true);
			try {
				Object value = field.get(obj);
				if (value != null) {
					if ("id".equals(field.getName().toLowerCase())
							|| "serialversionuid".equals(field.getName().toLowerCase()))
						continue;
					if (isFirst) {
						update = Update.update(field.getName(), value);
						isFirst = false;
					} else {
						update = update.set(field.getName(), value);
					}
				}

			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}
		}
		return update;
	}

	/**
	 * ���ݶ�����mongodb Query���
	 * 
	 * 1.ʱ�䷶Χ��ѯ����ʱ���ֶ�ǰ����begin��end��Ϊ�������ֶηֱ�ֵ ����private Date createDate; ��ʼʱ��
	 * private Date beginCreateDate; ����ʱ�� private Date endCreateDate;
	 * ����������where createDate >= beginCreateDate and createDate <
	 * beginCreateDate
	 * 
	 * 2.���� ���岢��ֵVO�� orderBy �ֶΣ���Ӣ�ġ�,���ָ��������Կո�ָ������� asc�ɲ�д ����private String
	 * orderBy; orderBy="createDate desc,sendDate asc,id" �����ṹ��order by
	 * createDate desc,sendDate asc,id asc
	 * 
	 * 3.�̶�ֵ���� ���岢��ֵVO�е������ֶΣ�����ʱ����Ը�ֵ���ֶε���Ϊ��������
	 */

	public static Query getQueryObj(final Object obj) {
		if (obj == null)
			return null;
		Field[] fields = obj.getClass().getDeclaredFields();
		// Sort sort=new Sort(new Order(Direction.DESC,"createDate"));
		Query query = new Query();
		// ������ڷ�Χ����ȷ������
		Map<String, Criteria> dateMap = new HashMap<String, Criteria>();
		String sortStr = null;
		for (Field field : fields) {
			field.setAccessible(true);
			try {
				Object value = field.get(obj);
				if (value != null) {
					if ("serialversionuid".equals(field.getName().toLowerCase())) {
						continue;
					}
					if ("orderby".equals(field.getName().toLowerCase())) {
						sortStr = String.valueOf(value);
						continue;
					}
					if (field.getType().getSimpleName().equals("Date")) {
						if (field.getName().toLowerCase().startsWith("begin")) {
							String beginName = field.getName().substring(5);
							if (beginName.isEmpty()) {
								dateMap.put("begin", Criteria.where("begin").is(value));
							} else {
								// beginName = StringUtil
								// .toLowerCaseFirstOne(beginName);
								Criteria criteria = dateMap.get(beginName) == null
										? Criteria.where(beginName).gte(value) : dateMap.get(beginName).gte(value);
								dateMap.put(beginName, criteria);
							}
							continue;
						}
						if (field.getName().toLowerCase().startsWith("end")) {
							String endName = field.getName().substring(3);
							if (endName.isEmpty()) {
								dateMap.put("end", Criteria.where("end").is(value));
							} else {
								// endName = StringUtil
								// .toLowerCaseFirstOne(endName);
								Criteria criteria = dateMap.get(endName) == null ? Criteria.where(endName).lt(value)
										: dateMap.get(endName).lt(value);
								dateMap.put(endName, criteria);
							}
							continue;
						}
						dateMap.put(field.getName(), Criteria.where(field.getName()).is(value));
						continue;
					}
					query.addCriteria(Criteria.where(field.getName()).is(value));
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		// �������Ͳ�ѯ����
		for (String key : dateMap.keySet()) {
			if (dateMap.get(key) != null) {
				query.addCriteria(dateMap.get(key));
			}
		}
		// ����
		if (sortStr != null && !sortStr.trim().isEmpty()) {
			Sort sort = null;
			String[] strs = sortStr.split(",");
			for (String str : strs) {
				str = str.trim();
				if (str.isEmpty()) {
					continue;
				}
				int i = str.indexOf(" ");
				if (i < 0) {
					if (sort == null) {
						sort = new Sort(Direction.ASC, str);
					} else {
						sort = sort.and(new Sort(Direction.ASC, str));
					}
				} else {
					String name = str.substring(0, i);
					String dire = str.substring(i + 1).trim();
					Sort sn = null;
					if ("desc".equals(dire.toLowerCase())) {
						sn = new Sort(Direction.DESC, name);
					} else {
						sn = new Sort(Direction.ASC, name);
					}
					if (sort == null) {
						sort = sn;
					} else {
						sort = sort.and(sn);
					}
				}
			}
			if (sort != null) {
				query.with(sort);
			}
		}
		return query;
	}
}
