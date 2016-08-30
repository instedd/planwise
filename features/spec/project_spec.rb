describe "Project" do
  before(:each) {
    log_in
    create_project("Foo")
  }

  it "should search project" do
    goto_page HomePage do |page|  
      fill_in "search", :with => "Foo"
    end 
  end

  it "should delete a project" do
    goto_page HomePage do |page|
      expect(page).to have_content("Foo")
      open_project_view
    end
    expect_page ProjectPage do |page|
      page.delete.click
      page.page.driver.browser.switch_to.alert.accept
    end
    expect_page HomePage do |page|
      expect(page).to_not have_content("Foo")
    end
  end

  it "should filter facilities by type" do
    goto_page HomePage do |page|
      page.find(".project-card").click
    end
    expect_page ProjectPage do |page|
      #replace with other element
      page.all(".icon-small")[1].click
    end
  end

  it "should set transport means options" do
    goto_page HomePage do |page|
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

end