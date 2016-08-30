module FeatureSpecHelpers
  def goto_page(klass, args = {})
    page = klass.new
    page.load args
    yield page if block_given?
  end

  def expect_page(klass)
    page = klass.new
    expect(page).to be_displayed
    yield page if block_given?
  end

  def log_in
    goto_page HomePage do
      expect_page GuissoLogin do |page|
        page.form.user_name.set "admin@instedd.org"
        page.form.password.set "admin123"
        page.form.login.click
      end
    end
  end

  def create_project(name)
    expect_page HomePage do |page|  
      page.press_primary_button 
      fill_in  "goal", :with => "#{name}"
      expand_locations_options
      select_location(1)
      submit
    end 
  end

  def expand_locations_options
    page.find(".rc-dropdown b").click
  end

  def select_location(option)
    page.find(".chosen-drop li:nth-child(#{option})").click
  end

  def submit
    page.all(".primary")[1].click
  end

  def open_project_view
    page.find(".project-card").click
  end

  def accept_alert
    page.driver.browser.switch_to.alert.accept
  end
end