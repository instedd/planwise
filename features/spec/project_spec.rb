describe "Project" do
  before(:each) {
    log_in
    #create_project
  }

  it "should search project" do
    expect_page HomePage do |page|  
      fill_in  "search", :with => "Foo"
    end 
  end

  it "should delete a project" do
    expect_page HomePage do |page|
      page.find(".project-card").click
    end
  end

  it "should filter facilities by type" do
    expect_page HomePage do |page|
      page.find(".project-card").click
    end
  end

  it "should set transport means options" do
    expect_page HomePage do |page|
      page.find(".project-card").click
    end
  end

  it "should verify that only allowed users can access a project" do
    #session_destroy
    goto_page HomePage do
      expect_page GuissoLogin do |page|
        page.form.user_name.set "user@instedd.org"
        page.form.password.set "user123"
        page.form.login.click
      end
    end  
  end
  
  context "create" do
    it "should create a project" do
      expect_page HomePage do |page|  
        page.press_primary_button 
        fill_in  "goal", :with => "Foo"
        expand_locations_options
        select_location(1)
        submit
      end 
    end
  end
  
end