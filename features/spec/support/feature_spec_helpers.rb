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
    goto_page HomePage do |page|  
      page.press_primary_button 
      fill_in  "goal", :with => "#{name}"
      expand_options
      select_option(1)
      expand_locations_options
      select_location(1)
      submit
    end 
  end

  def create_dataset
    goto_page DatasetsPage do |page|
      page.press_primary_button 

      new_window = window_opened_by { 
        page.authorise.click
      }

      within_window new_window do
        click_button 'Approve'
        sleep 3
      end

      page.find(".collections li").click
      expand_options
      select_option(1)
      page.import.click
      sleep 3
      expect(page).to have_content 'Ready to use'
    end
  end

  def expand_options
    page.all(".rc-dropdown b")[0].click
  end

  def select_option(option)
    page.find(".chosen-drop li:nth-child(#{option})").click
  end

  def expand_locations_options
    page.all(".rc-dropdown b")[1].click
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

  def check_facility_type
    page.all('input[type="checkbox"]')[0].click
  end

  def screenshot_image
    screenshot_and_save_page
    screenshots = Dir['/features/tmp/*.png'].sort_by { |x| File.mtime(x) }
    last = screenshots.last
    Phashion::Image.new(last)
  end
end