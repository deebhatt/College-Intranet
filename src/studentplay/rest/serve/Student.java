package studentplay.rest.serve;


public class Student {

	private int id;
	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getAge() {
		return age;
	}

	public void setAge(int age) {
		this.age = age;
	}

	private String name;
	private int age;
	
	public Student(int id, String name, int age)
	{
		this.id = id;
		this.name = name;
		this.age = age;
	}
	
	@Override
	public String toString()
	{
		return new StringBuffer("ID :").append(this.id)
				.append("Name :").append(this.name)
				.append("Age :").append(this.age).toString();
	}
}
