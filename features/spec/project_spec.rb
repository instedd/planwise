describe "Project" do
  before(:each) {
    log_in
    create_dataset
    create_project("Foo")
  }

  it "should search project" do
    goto_page HomePage do |page|
      create_project("Bar")  
    end
    goto_page HomePage do |page| 
      fill_in "search", :with => "Foo"
    end 
    expect_page HomePage do |page|
      expect(page).to have_content("Foo")
      expect(page).to_not have_content("Bar")
    end
  end

  it "should delete a project" do
    goto_page HomePage do |page|
      expect(page).to have_content("Foo")
      open_project_view
    end
    expect_page ProjectPage do |page|
      page.delete.click
      accept_alert
    end
    expect_page HomePage do |page|
      expect(page).to_not have_content("Foo")
    end
  end

  it "should access only allowed projects" do
    goto_page HomePage do |page|
      page.press_signout_button
      expect_page GuissoLogin do |page|
        page.form.user_name.set "user@instedd.org"
        page.form.password.set "user1234"
        page.form.login.click
      end
      expect_page HomePage do |page|
        expect(page).to_not have_content("Foo")
        expect(page).to have_content("You have no projects yet")
      end
    end  
  end
end 