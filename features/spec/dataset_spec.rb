describe "Dataset" do
  before(:each) {
    log_in
  }

  it "should create dataset" do
    goto_page DatasetsPage do |page|
      page.press_primary_button 

      new_window = window_opened_by { 
        page.authorise.click
      }

      within_window new_window do
        click_button 'Approve'
      end

      page.find(".collections li").click
      expand_options
      select_option(1)
      page.import.click
      sleep 3
      expect(page).to have_content 'Ready to use'
    end 
  end
end