describe "Project" do
  before(:each) {
    log_in
    create_dataset
  }
    context "projects listing" do
      before(:each) {   
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
          page.press_signout_button
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
            page.press_signout_button
          end
        end  
      end
    end

    context "project view" do
      before(:each) {   
        create_project("Foo")
      }

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
          page.press_signout_button
        end
      end

      it "should update transport means" do
        goto_page HomePage do |page|
          open_project_view
        end

        expect_page ProjectPage do |page|
          page.header.open_transport_means
        end

        expect_page ProjectTransportMeansPage do |page|
          screenshot_and_save_page
          expand_options
          select_option(3)
          screenshot_and_save_page
          #compare screenshots (wip)
        end
      end       
    end

    context "without project" do
      it "should create project" do
        goto_page HomePage do |page|  
          page.press_primary_button 
          fill_in  "goal", :with => "FooBar"
          expand_options
          select_option(1)
          expand_locations_options
          select_location(1)
          submit
          page.press_signout_button
        end 
      end
    end
end 